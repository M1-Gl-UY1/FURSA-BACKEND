package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.EscrowProprieteResponse;
import com.fursa.fursa_backend.dto.EscrowTransactionResponse;
import com.fursa.fursa_backend.model.EscrowPropriete;
import com.fursa.fursa_backend.model.EscrowTransaction;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutEscrow;
import com.fursa.fursa_backend.model.enumeration.StatutPossession;
import com.fursa.fursa_backend.model.enumeration.TypeEscrowTransaction;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.model.enumeration.TypeWalletTransaction;
import com.fursa.fursa_backend.repository.EscrowProprieteRepository;
import com.fursa.fursa_backend.repository.EscrowTransactionRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 10c : service central des escrows de propriete.
 *
 * Source de verite du compte sequestre par propriete. Toute mutation passe par
 * credit() ou debit() pour garantir l'append-only sur EscrowTransaction et
 * l'optimistic locking sur EscrowPropriete.
 *
 * Ne s'occupe PAS de la mecanique d'achat ni de la mecanique de retrait :
 * - L'achat (debit wallet + credit escrow + Possession) est dans MarchePrimaireService
 * - Le retrait proprio (debit escrow + credit wallet + commission FURSA) sera Phase 10e
 *
 * S'occupe uniquement de la coherence du solde + journal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EscrowService {

    private final EscrowProprieteRepository escrowRepository;
    private final EscrowTransactionRepository txRepository;
    private final ProprieteRepository proprieteRepository;
    private final PossessionRepository possessionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    // =========================================================================
    // Lookup / Creation
    // =========================================================================

    @Transactional
    public EscrowPropriete getOrCreate(Long proprieteId) {
        return escrowRepository.findByProprieteId(proprieteId)
                .orElseGet(() -> createForPropriete(proprieteId));
    }

    @Transactional
    public EscrowPropriete createForPropriete(Long proprieteId) {
        if (escrowRepository.existsByProprieteId(proprieteId)) {
            return escrowRepository.findByProprieteId(proprieteId).orElseThrow();
        }
        Propriete p = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + proprieteId));

        EscrowPropriete e = new EscrowPropriete();
        e.setPropriete(p);
        e.setSolde(BigDecimal.ZERO);
        e.setTotalCollecte(BigDecimal.ZERO);
        e.setStatut(StatutEscrow.EN_COLLECTE);
        // Phase 10c ajustement (Hugh 22/05/2026) : 100% (toutes parts vendues) avant deblocage.
        e.setSeuilPct(100);
        EscrowPropriete saved = escrowRepository.save(e);
        log.info("Escrow cree pour propriete {} : id={}", proprieteId, saved.getId());
        return saved;
    }

    // =========================================================================
    // Mouvements
    // =========================================================================

    /**
     * Credite l'escrow (achat investisseur). Le wallet investisseur a deja ete debite
     * par WalletService AVANT cet appel (transaction englobante coté MarchePrimaireService).
     *
     * Phase 10c bis : apres credit, declenche automatiquement la transition vers
     * FINANCEE si le seuil de collecte est atteint (toutes Possessions PENDING -> ACTIVE).
     */
    @Transactional
    public EscrowTransaction creditAchat(Long proprieteId, BigDecimal montant,
                                          Long investisseurId,
                                          String refTable, Long refId, String libelle) {
        if (montant == null || montant.signum() <= 0) {
            throw new IllegalArgumentException("Le montant credite doit etre strictement positif.");
        }
        EscrowPropriete e = getOrCreate(proprieteId);
        if (e.getStatut() == StatutEscrow.ANNULEE) {
            throw new IllegalStateException(
                    "Achat impossible : la collecte de cette propriete est annulee.");
        }
        BigDecimal m = montant.setScale(2, RoundingMode.HALF_UP);
        e.setSolde(e.getSolde().add(m));
        e.setTotalCollecte(e.getTotalCollecte().add(m));
        escrowRepository.save(e);

        EscrowTransaction tx = new EscrowTransaction();
        tx.setEscrow(e);
        tx.setType(TypeEscrowTransaction.CREDIT_ACHAT);
        tx.setMontant(m);
        tx.setSoldeApres(e.getSolde());
        tx.setInvestisseurId(investisseurId);
        tx.setRefTable(refTable);
        tx.setRefId(refId);
        tx.setLibelle(libelle);
        EscrowTransaction saved = txRepository.save(tx);

        log.info("Escrow {} (prop {}) : credit achat +{} USD par inv {}. Solde : {}",
                e.getId(), proprieteId, m, investisseurId, e.getSolde());

        // Phase 10c bis : auto-transition si seuil atteint
        checkAndTransitionToFinancee(e);

        return saved;
    }

    /**
     * Phase 10c bis : verifie si le seuil de collecte est atteint et fait passer
     * l'escrow EN_COLLECTE -> FINANCEE. Cela active toutes les Possessions PENDING
     * de la propriete et notifie le proprietaire + les investisseurs.
     *
     * Idempotent : si l'escrow est deja FINANCEE ou ANNULEE, ne fait rien.
     */
    @Transactional
    public void checkAndTransitionToFinancee(EscrowPropriete e) {
        if (e.getStatut() != StatutEscrow.EN_COLLECTE) return;

        Propriete p = e.getPropriete();
        if (p == null) return;

        BigDecimal montantCible = montantCible(p);
        if (montantCible.signum() <= 0) return;

        BigDecimal seuilMontant = montantCible
                .multiply(BigDecimal.valueOf(e.getSeuilPct()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (e.getTotalCollecte().compareTo(seuilMontant) < 0) {
            return; // Seuil pas encore atteint
        }

        // Seuil atteint : transition vers FINANCEE
        e.setStatut(StatutEscrow.FINANCEE);
        e.setFinanceeLe(LocalDateTime.now());
        escrowRepository.save(e);

        log.info("Escrow {} (prop {}) PASSE FINANCEE. Seuil {}% atteint avec {} / {} USD collectes.",
                e.getId(), p.getId(), e.getSeuilPct(), e.getTotalCollecte(), montantCible);

        // Activer toutes les Possessions PENDING de cette propriete
        List<Possession> possessions = possessionRepository.findByProprieteId(p.getId());
        int activees = 0;
        for (Possession pos : possessions) {
            if (pos.getStatut() == StatutPossession.PENDING) {
                pos.setStatut(StatutPossession.ACTIVE);
                possessionRepository.save(pos);
                activees++;
                // Notifier l'investisseur
                Investisseur inv = pos.getInvestisseur();
                if (inv != null) {
                    notificationService.envoyer(
                            inv,
                            "Vos parts sont actives !",
                            "La collecte de \"" + p.getNom() + "\" a atteint "
                                    + e.getSeuilPct() + "%. Vos " + pos.getNombreDeParts()
                                    + " part(s) sont desormais actives et vous commencerez a percevoir "
                                    + "les dividendes a la prochaine distribution trimestrielle.",
                            TypeMessage.ANNONCE
                    );
                }
            }
        }

        // Notifier le proprietaire
        if (p.getProposeurId() != null) {
            userRepository.findById(p.getProposeurId()).ifPresent(u -> {
                if (u instanceof Investisseur prop) {
                    notificationService.envoyer(
                            prop,
                            "Votre bien est entierement finance !",
                            "Felicitations. Le bien \"" + p.getNom() + "\" a atteint le seuil de "
                                    + e.getSeuilPct() + "% (collecte totale : " + e.getTotalCollecte()
                                    + " USD). Vous pouvez maintenant demander un retrait des fonds via votre "
                                    + "espace propriétaire (validation admin requise).",
                            TypeMessage.ANNONCE
                    );
                }
            });
        }

        log.info("Escrow {} : {} Possession(s) PENDING -> ACTIVE", e.getId(), activees);
    }

    /**
     * Phase 10c bis : annule manuellement une collecte (action admin).
     * Refund integral de tous les investisseurs (credit wallet en USD), Possessions
     * passees a ANNULEE, escrow ferme avec motif.
     */
    @Transactional
    public void annulerCollecte(Long proprieteId, String motif) {
        if (motif == null || motif.trim().length() < 10) {
            throw new IllegalArgumentException("Motif obligatoire (min 10 caracteres) pour annuler une collecte.");
        }
        EscrowPropriete e = escrowRepository.findByProprieteId(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Escrow introuvable pour prop " + proprieteId));
        if (e.getStatut() == StatutEscrow.ANNULEE) {
            throw new IllegalStateException("Cette collecte est deja annulee.");
        }
        if (e.getStatut() == StatutEscrow.FINANCEE) {
            throw new IllegalStateException(
                    "Impossible d'annuler une collecte deja financee (les fonds ont ete debloques). "
                            + "Utilisez une procedure manuelle hors-systeme pour ce cas.");
        }

        Propriete p = e.getPropriete();
        if (p == null) {
            throw new IllegalStateException("Escrow sans propriete attachee, annulation impossible.");
        }

        // Refund : pour chaque investisseur ayant achete, crediter son wallet du total qu'il a verse
        List<EscrowTransaction> achats = txRepository.findByEscrowIdOrderByCreatedAtDesc(e.getId());
        java.util.Map<Long, BigDecimal> totalParInvestisseur = new java.util.HashMap<>();
        for (EscrowTransaction t : achats) {
            if (t.getType() == TypeEscrowTransaction.CREDIT_ACHAT && t.getInvestisseurId() != null) {
                totalParInvestisseur.merge(t.getInvestisseurId(), t.getMontant(), BigDecimal::add);
            }
        }

        // int[] pour pouvoir muter dans les lambdas de notification
        int[] refundsCount = {0};
        for (var entry : totalParInvestisseur.entrySet()) {
            Long invId = entry.getKey();
            BigDecimal montantRefund = entry.getValue();
            if (montantRefund.signum() <= 0) continue;

            // Debiter l'escrow + creer la trace
            this.debit(e.getId(), montantRefund,
                    TypeEscrowTransaction.DEBIT_REFUND_INVESTISSEUR,
                    invId, "annulation_collecte", null,
                    "Refund annulation : " + motif);

            // Crediter le wallet investisseur
            walletService.credit(invId, montantRefund,
                    TypeWalletTransaction.CREDIT_REFUND_ACHAT,
                    "Remboursement integral : collecte annulee pour " + p.getNom(),
                    "escrow_propriete", e.getId(),
                    null);

            // Notifier
            userRepository.findById(invId).ifPresent(u -> {
                if (u instanceof Investisseur inv) {
                    notificationService.envoyer(
                            inv,
                            "Remboursement integral suite a l'annulation d'une collecte",
                            "La collecte de \"" + p.getNom() + "\" a ete annulee. Votre investissement de "
                                    + montantRefund + " USD a ete integralement recredite sur votre wallet. "
                                    + "Motif : " + motif,
                            TypeMessage.AVERTISSEMENT
                    );
                }
            });
            refundsCount[0]++;
        }

        // Marquer les Possessions de cette propriete ANNULEE
        List<Possession> possessions = possessionRepository.findByProprieteId(p.getId());
        for (Possession pos : possessions) {
            if (pos.getStatut() != StatutPossession.ANNULEE) {
                pos.setStatut(StatutPossession.ANNULEE);
                possessionRepository.save(pos);
            }
        }

        // Fermer l'escrow
        e.setStatut(StatutEscrow.ANNULEE);
        e.setAnnuleeLe(LocalDateTime.now());
        e.setMotifAnnulation(motif);
        escrowRepository.save(e);

        // Notifier le proprietaire
        final int nbRefunds = refundsCount[0];
        if (p.getProposeurId() != null) {
            userRepository.findById(p.getProposeurId()).ifPresent(u -> {
                if (u instanceof Investisseur prop) {
                    notificationService.envoyer(
                            prop,
                            "Collecte annulee",
                            "La collecte pour le bien \"" + p.getNom() + "\" a ete annulee par un admin. "
                                    + nbRefunds + " investisseur(s) ont ete rembourses. Motif : " + motif,
                            TypeMessage.AVERTISSEMENT
                    );
                }
            });
        }

        log.info("Escrow {} (prop {}) ANNULEE : {} refunds effectues. Motif : {}",
                e.getId(), p.getId(), nbRefunds, motif);
    }

    /**
     * Debit l'escrow (retrait proprio, commission, refund, ajustement).
     * Refuse si solde insuffisant.
     */
    @Transactional
    public EscrowTransaction debit(Long escrowId, BigDecimal montant,
                                    TypeEscrowTransaction type,
                                    Long investisseurId, String refTable, Long refId,
                                    String libelle) {
        if (montant == null || montant.signum() <= 0) {
            throw new IllegalArgumentException("Le montant debite doit etre strictement positif.");
        }
        EscrowPropriete e = escrowRepository.findById(escrowId)
                .orElseThrow(() -> new EntityNotFoundException("Escrow introuvable : " + escrowId));
        BigDecimal m = montant.setScale(2, RoundingMode.HALF_UP);
        if (e.getSolde().compareTo(m) < 0) {
            throw new IllegalStateException("Solde escrow insuffisant : " + e.getSolde()
                    + " EUR disponibles, " + m + " EUR demandes.");
        }
        e.setSolde(e.getSolde().subtract(m));
        escrowRepository.save(e);

        EscrowTransaction tx = new EscrowTransaction();
        tx.setEscrow(e);
        tx.setType(type);
        tx.setMontant(m.negate());
        tx.setSoldeApres(e.getSolde());
        tx.setInvestisseurId(investisseurId);
        tx.setRefTable(refTable);
        tx.setRefId(refId);
        tx.setLibelle(libelle);
        return txRepository.save(tx);
    }

    // =========================================================================
    // Statut & lecture
    // =========================================================================

    public EscrowProprieteResponse getStatut(Long proprieteId) {
        EscrowPropriete e = getOrCreate(proprieteId);
        return toResponse(e);
    }

    public List<EscrowTransactionResponse> historique(Long proprieteId) {
        EscrowPropriete e = escrowRepository.findByProprieteId(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Escrow introuvable pour prop " + proprieteId));
        return txRepository.findByEscrowIdOrderByCreatedAtDesc(e.getId())
                .stream().map(this::toResponse).toList();
    }

    public List<EscrowProprieteResponse> listerTous() {
        return escrowRepository.findAll().stream().map(this::toResponse).toList();
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    public EscrowProprieteResponse toResponse(EscrowPropriete e) {
        Propriete p = e.getPropriete();
        BigDecimal montantCible = montantCible(p);
        BigDecimal montantSeuil = montantCible
                .multiply(BigDecimal.valueOf(e.getSeuilPct()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        double pct = 0.0;
        if (montantCible.signum() > 0) {
            pct = e.getTotalCollecte()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(montantCible, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        return new EscrowProprieteResponse(
                e.getId(),
                p == null ? null : p.getId(),
                p == null ? null : p.getNom(),
                e.getSolde(),
                e.getTotalCollecte(),
                montantCible,
                montantSeuil,
                pct,
                e.getSeuilPct(),
                e.getStatut(),
                e.getCreatedAt(),
                e.getFinanceeLe(),
                e.getAnnuleeLe(),
                e.getMotifAnnulation()
        );
    }

    private BigDecimal montantCible(Propriete p) {
        if (p == null || p.getNombreTotalPart() == null || p.getPrixUnitairePart() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(p.getNombreTotalPart())
                .multiply(p.getPrixUnitairePart())
                .setScale(2, RoundingMode.HALF_UP);
    }

    public EscrowTransactionResponse toResponse(EscrowTransaction t) {
        return new EscrowTransactionResponse(
                t.getId(),
                t.getEscrow().getId(),
                t.getType(),
                t.getMontant(),
                t.getSoldeApres(),
                t.getInvestisseurId(),
                t.getRefTable(),
                t.getRefId(),
                t.getLibelle(),
                t.getMetadata(),
                t.getCreatedAt()
        );
    }
}
