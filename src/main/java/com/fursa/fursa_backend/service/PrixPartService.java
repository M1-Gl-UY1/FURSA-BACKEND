package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.HistoriquePrixPartResponse;
import com.fursa.fursa_backend.model.HistoriquePrixPart;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.model.enumeration.RaisonRecalculPrix;
import com.fursa.fursa_backend.repository.HistoriquePrixPartRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * P1 (Hugh 22/05/2026) : prix dynamique des parts.
 *
 * Implementation de la formule documentee dans PRIX_DYNAMIQUE_FURSA.md :
 *   prix_courant = prix_initial * (1 + bonus_rentabilite + bonus_demande)
 *
 * Le service recoit les evenements declencheurs (revenu valide, changement
 * liste d'attente, cron trimestriel, ajustement admin) et recalcule le prix
 * en consequence. Chaque recalcul produit un snapshot dans historique_prix_part.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrixPartService {

    private final ProprieteRepository proprieteRepository;
    private final HistoriquePrixPartRepository historiqueRepository;

    // ========================================================================
    // Constantes de la formule (voir PRIX_DYNAMIQUE_FURSA.md §3.3, §4.3, §5)
    // ========================================================================

    /** Coefficient de lissage applique a l'ecart de rentabilite. */
    private static final BigDecimal LISSAGE_RENTABILITE = new BigDecimal("0.005");

    /** Cap individuel de contribution rentabilite par trimestre (±10%). */
    private static final BigDecimal CAP_CONTRIBUTION_TRIMESTRIELLE = new BigDecimal("0.10");

    /** Cap global du bonus rentabilite cumule (±40%). */
    private static final BigDecimal CAP_BONUS_RENTABILITE_TOTAL = new BigDecimal("0.40");

    /** Coefficient applique au ratio liste d'attente / nombre total parts. */
    private static final BigDecimal COEF_DEMANDE = new BigDecimal("0.40");

    /** Cap du bonus demande (+30%, jamais negatif). */
    private static final BigDecimal CAP_BONUS_DEMANDE = new BigDecimal("0.30");

    /** Plancher global : le prix ne peut pas descendre sous 50% du prix initial. */
    private static final BigDecimal PLANCHER_PRIX = new BigDecimal("0.50");

    /** Plafond global : le prix ne peut pas depasser 200% du prix initial. */
    private static final BigDecimal PLAFOND_PRIX = new BigDecimal("2.00");

    // ========================================================================
    // API publique
    // ========================================================================

    /**
     * Recalcule le prix courant d'une propriete et journalise un snapshot
     * dans l'historique. Idempotent : si un snapshot existe deja pour le
     * couple (raison, sourceId) non null, l'appel est ignore.
     *
     * @return la nouvelle entree d'historique creee, ou la derniere existante
     *         si l'appel etait idempotent.
     */
    @Transactional
    public HistoriquePrixPartResponse recalculer(Long proprieteId,
                                                  RaisonRecalculPrix raison,
                                                  Long sourceId) {
        Propriete p = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Propriete non trouvee: id=" + proprieteId));

        // Idempotence : si (raison, sourceId) existe deja, on ne recalcule pas.
        if (sourceId != null && raison != RaisonRecalculPrix.CRON_TRIMESTRIEL) {
            List<HistoriquePrixPart> existant = historiqueRepository
                    .findByProprieteIdOrderByCreatedAtDesc(proprieteId);
            boolean dejaPresent = existant.stream().anyMatch(h ->
                    h.getRaison() == raison && sourceId.equals(h.getSourceId()));
            if (dejaPresent) {
                log.debug("[PrixPart] Recalcul idempotent ignore : prop={} raison={} source={}",
                        proprieteId, raison, sourceId);
                return toResponse(existant.get(0));
            }
        }

        BigDecimal prixInitial = ensurePrixInitial(p);
        BigDecimal bonusRenta = nonNull(p.getBonusRentabiliteTotal());
        BigDecimal bonusDemande = nonNull(p.getBonusDemande());

        // prix_courant = prix_initial * (1 + bonus_renta + bonus_demande)
        BigDecimal facteur = BigDecimal.ONE.add(bonusRenta).add(bonusDemande);
        BigDecimal prixBrut = prixInitial.multiply(facteur)
                .setScale(2, RoundingMode.HALF_UP);

        // Bornes de securite globales (voir doc §5)
        BigDecimal min = prixInitial.multiply(PLANCHER_PRIX).setScale(2, RoundingMode.HALF_UP);
        BigDecimal max = prixInitial.multiply(PLAFOND_PRIX).setScale(2, RoundingMode.HALF_UP);
        BigDecimal prixFinal = prixBrut.max(min).min(max);

        if (prixBrut.compareTo(min) < 0) {
            log.warn("[PrixPart] PLANCHER atteint pour prop={} : prix calcule {} < min {}",
                    proprieteId, prixBrut, min);
        }
        if (prixBrut.compareTo(max) > 0) {
            log.warn("[PrixPart] PLAFOND atteint pour prop={} : prix calcule {} > max {}",
                    proprieteId, prixBrut, max);
        }

        // Mise a jour de la propriete (prix courant)
        p.setPrixUnitairePart(prixFinal);
        proprieteRepository.save(p);

        // Snapshot dans l'historique
        BigDecimal variationPct = prixFinal.subtract(prixInitial)
                .multiply(new BigDecimal("100"))
                .divide(prixInitial, 4, RoundingMode.HALF_UP);

        HistoriquePrixPart snapshot = new HistoriquePrixPart();
        snapshot.setPropriete(p);
        snapshot.setPrixUnitaire(prixFinal);
        snapshot.setPrixInitial(prixInitial);
        snapshot.setBonusRentabiliteTotal(bonusRenta);
        snapshot.setBonusDemande(bonusDemande);
        snapshot.setRaison(raison);
        snapshot.setSourceId(sourceId);
        snapshot.setVariationPct(variationPct);

        HistoriquePrixPart saved = historiqueRepository.save(snapshot);
        log.info("[PrixPart] Recalcul prop={} {} -> {} ({}%) raison={}",
                proprieteId, prixInitial, prixFinal, variationPct, raison);

        return toResponse(saved);
    }

    /**
     * Applique une contribution trimestrielle de rentabilite issue d'un revenu
     * valide. Met a jour bonus_rentabilite_total dans les caps, puis recalcule
     * le prix.
     *
     * Voir PRIX_DYNAMIQUE_FURSA.md §3.2.
     */
    @Transactional
    public void appliquerRevenuValide(Propriete p, Revenus revenu) {
        if (p.getPrixVenteTotal() == null || p.getFractionVenduePct() == null
                || p.getFractionVenduePct() <= 0) {
            log.debug("[PrixPart] Bien sans prix/fraction definie, recalcul ignore : prop={}", p.getId());
            return;
        }

        BigDecimal valeurMisEnVente = p.getPrixVenteTotal()
                .multiply(BigDecimal.valueOf(p.getFractionVenduePct()))
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        if (valeurMisEnVente.signum() <= 0) {
            log.warn("[PrixPart] Valeur mise en vente <= 0, recalcul ignore : prop={}", p.getId());
            return;
        }

        BigDecimal revenuNet = revenu.getMontantDistribuable();
        BigDecimal rentabiliteReelleAnnuelle = revenuNet
                .divide(valeurMisEnVente, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("4"))
                .multiply(new BigDecimal("100"));

        BigDecimal prevue = BigDecimal.valueOf(
                p.getRentabilitePrevue() == null ? 0.0 : p.getRentabilitePrevue());
        BigDecimal ecart = rentabiliteReelleAnnuelle.subtract(prevue);

        // contribution = ecart * 0.005, capee a ±10% sur le trimestre
        BigDecimal contribution = ecart.multiply(LISSAGE_RENTABILITE);
        if (contribution.compareTo(CAP_CONTRIBUTION_TRIMESTRIELLE) > 0) {
            contribution = CAP_CONTRIBUTION_TRIMESTRIELLE;
        } else if (contribution.compareTo(CAP_CONTRIBUTION_TRIMESTRIELLE.negate()) < 0) {
            contribution = CAP_CONTRIBUTION_TRIMESTRIELLE.negate();
        }

        BigDecimal bonusActuel = nonNull(p.getBonusRentabiliteTotal());
        BigDecimal bonusNouveau = bonusActuel.add(contribution);

        // Cap global ±40%
        if (bonusNouveau.compareTo(CAP_BONUS_RENTABILITE_TOTAL) > 0) {
            bonusNouveau = CAP_BONUS_RENTABILITE_TOTAL;
        } else if (bonusNouveau.compareTo(CAP_BONUS_RENTABILITE_TOTAL.negate()) < 0) {
            bonusNouveau = CAP_BONUS_RENTABILITE_TOTAL.negate();
        }

        p.setBonusRentabiliteTotal(bonusNouveau.setScale(6, RoundingMode.HALF_UP));
        proprieteRepository.save(p);

        log.info("[PrixPart] Revenu valide prop={} : renta_reelle={}% ecart={} contrib={} bonus_total={}",
                p.getId(), rentabiliteReelleAnnuelle, ecart, contribution, bonusNouveau);

        recalculer(p.getId(), RaisonRecalculPrix.DECLARATION_REVENU_VALIDEE, revenu.getId());
    }

    /**
     * Met a jour le bonus_demande a partir du ratio liste d'attente / total parts.
     * Appelle directement le recalcul de prix.
     *
     * Note : la liste d'attente est livree dans le chantier P2. Tant que P2
     * n'est pas en place, cette methode peut etre appelee avec partsEnAttente=0
     * et ne change rien.
     *
     * Voir PRIX_DYNAMIQUE_FURSA.md §4.
     */
    @Transactional
    public void appliquerChangementListeAttente(Long proprieteId,
                                                  int partsEnAttente,
                                                  Long sourceId) {
        Propriete p = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Propriete non trouvee: id=" + proprieteId));

        int totalParts = p.getNombreTotalPart() == null ? 0 : p.getNombreTotalPart();
        if (totalParts <= 0) {
            log.debug("[PrixPart] Total parts <= 0, recalcul demande ignore : prop={}", proprieteId);
            return;
        }

        BigDecimal ratio = BigDecimal.valueOf(partsEnAttente)
                .divide(BigDecimal.valueOf(totalParts), 6, RoundingMode.HALF_UP);
        BigDecimal bonusDemande = ratio.multiply(COEF_DEMANDE);

        if (bonusDemande.compareTo(CAP_BONUS_DEMANDE) > 0) {
            bonusDemande = CAP_BONUS_DEMANDE;
        }
        if (bonusDemande.signum() < 0) {
            bonusDemande = BigDecimal.ZERO;
        }

        p.setBonusDemande(bonusDemande.setScale(6, RoundingMode.HALF_UP));
        proprieteRepository.save(p);

        recalculer(proprieteId, RaisonRecalculPrix.LISTE_ATTENTE_CHANGEE, sourceId);
    }

    /** Liste l'historique des prix d'une propriete, du plus ancien au plus recent. */
    public List<HistoriquePrixPartResponse> historique(Long proprieteId) {
        return historiqueRepository.findByProprieteIdOrderByCreatedAtAsc(proprieteId)
                .stream().map(this::toResponse).toList();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Garantit que la propriete a un prix_initial_part defini.
     * Pour les biens crees avant la migration 015, on initialise a partir
     * du prix unitaire courant.
     */
    private BigDecimal ensurePrixInitial(Propriete p) {
        if (p.getPrixInitialPart() != null && p.getPrixInitialPart().signum() > 0) {
            return p.getPrixInitialPart();
        }
        BigDecimal prixCourant = p.getPrixUnitairePart();
        if (prixCourant == null || prixCourant.signum() <= 0) {
            throw new IllegalStateException(
                    "Propriete sans prix unitaire defini : id=" + p.getId());
        }
        p.setPrixInitialPart(prixCourant);
        proprieteRepository.save(p);
        return prixCourant;
    }

    private BigDecimal nonNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private HistoriquePrixPartResponse toResponse(HistoriquePrixPart h) {
        return new HistoriquePrixPartResponse(
                h.getId(),
                h.getPropriete() == null ? null : h.getPropriete().getId(),
                h.getPrixUnitaire(),
                h.getPrixInitial(),
                h.getBonusRentabiliteTotal(),
                h.getBonusDemande(),
                h.getRaison(),
                h.getSourceId(),
                h.getVariationPct(),
                h.getCreatedAt()
        );
    }
}
