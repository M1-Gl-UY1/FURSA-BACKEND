package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.RevenuRequest;
import com.fursa.fursa_backend.dto.RevenuResponse;
import com.fursa.fursa_backend.dto.StatutDeclarationResponse;
import com.fursa.fursa_backend.dto.SubmissionRevenuRequest;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.model.enumeration.StatutRevenu;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.RevenusRepository;
import com.fursa.fursa_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RevenuService {

    private final RevenusRepository revenusRepository;
    private final ProprieteRepository proprieteRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PrixPartService prixPartService;

    // =========================================================================
    // Création directe par admin (workflow historique)
    // =========================================================================

    @Transactional
    public RevenuResponse creer(RevenuRequest request) {
        Propriete propriete = proprieteRepository.findById(request.proprieteId())
                .orElseThrow(() -> new EntityNotFoundException("Propriete non trouvee: id=" + request.proprieteId()));

        Revenus revenu = new Revenus();
        revenu.setPropriete(propriete);
        revenu.setMontantTotal(request.montantTotal());
        revenu.setDate(request.date() != null ? request.date() : LocalDate.now());
        // Création directe par admin → directement VALIDE (peut être distribué tout de suite)
        revenu.setStatut(StatutRevenu.VALIDE);
        return toResponse(revenusRepository.save(revenu));
    }

    public List<RevenuResponse> lister() {
        return revenusRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<RevenuResponse> listerParPropriete(Long proprieteId) {
        return revenusRepository.findByProprieteId(proprieteId).stream().map(this::toResponse).toList();
    }

    public RevenuResponse getById(Long id) {
        return toResponse(revenusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + id)));
    }

    // =========================================================================
    // PHASE 8 : workflow déclaration propriétaire
    // =========================================================================

    @Transactional
    public RevenuResponse soumettre(Long proposeurId, SubmissionRevenuRequest req) {
        Propriete propriete = proprieteRepository.findById(req.proprieteId())
                .orElseThrow(() -> new EntityNotFoundException("Propriete non trouvee: id=" + req.proprieteId()));

        if (propriete.getProposeurId() == null || !propriete.getProposeurId().equals(proposeurId)) {
            throw new AccessDeniedException("Vous ne pouvez déclarer un revenu que pour vos propres biens.");
        }

        // P8b (Hugh 25/05/2026) : un bien en construction ne peut pas generer de revenus.
        if (propriete.getStatutExploitation() == com.fursa.fursa_backend.model.enumeration.StatutExploitation.EN_CONSTRUCTION) {
            throw new IllegalStateException(
                    "Ce bien est encore en construction (livraison prevue "
                            + (propriete.getDateLivraisonPrevue() == null ? "non renseignee" : propriete.getDateLivraisonPrevue())
                            + "). Aucune declaration de revenu n'est possible tant que le bien n'a pas ete livre.");
        }

        // V2 K (06/06/2026) : un bien sans investisseur n'a personne a qui
        // distribuer le dividende. La declaration est inutile et bloquee.
        Integer total = propriete.getNombreTotalPart();
        Integer dispo = propriete.getPartsDisponibles();
        int vendues = (total != null && dispo != null) ? (total - dispo) : 0;
        if (vendues <= 0) {
            throw new IllegalStateException(
                    "Ce bien n'a encore aucun investisseur. La declaration de revenu n'est possible "
                            + "qu'a partir du moment ou au moins une part a ete vendue.");
        }

        // Phase 10b : penalite forfaitaire si declaration apres le 5 du mois.
        LocalDate today = LocalDate.now();
        java.math.BigDecimal penalite = DeclarationWindowRules.penaliteApplicable(
                today, req.montantTotal());

        Revenus revenu = new Revenus();
        revenu.setPropriete(propriete);
        revenu.setMontantTotal(req.montantTotal());
        revenu.setDate(today);
        revenu.setProposeurId(proposeurId);
        revenu.setStatut(StatutRevenu.EN_REVIEW);
        revenu.setPeriodeDebut(req.periodeDebut());
        revenu.setPeriodeFin(req.periodeFin());
        revenu.setPenaliteRetard(penalite);

        Revenus saved = revenusRepository.save(revenu);

        // V2 K (06/06/2026) : suffixe penalite retire (penalite supprimee).
        notifierAdmins(
                "Nouvelle déclaration de revenu",
                "Le bien \"" + propriete.getNom() + "\" a une nouvelle déclaration de revenu en attente.",
                TypeMessage.INFO
        );

        return toResponse(saved);
    }

    public List<RevenuResponse> listerMesRevenus(Long proposeurId) {
        return revenusRepository.findByProposeurIdOrderByIdDesc(proposeurId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public RevenuResponse approuver(Long id) {
        Revenus r = revenusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + id));
        if (r.getStatut() != StatutRevenu.EN_REVIEW) {
            throw new IllegalStateException("Seuls les revenus EN_REVIEW peuvent être approuvés (statut actuel : " + r.getStatut() + ")");
        }
        r.setStatut(StatutRevenu.VALIDE);
        r.setMotifRefus(null);
        Revenus saved = revenusRepository.save(r);

        // P1 (Hugh 22/05/2026) : prix dynamique. Un revenu valide modifie le
        // bonus_rentabilite cumule de la propriete et recalcule le prix courant.
        // Voir PRIX_DYNAMIQUE_FURSA.md §3.
        if (saved.getPropriete() != null) {
            prixPartService.appliquerRevenuValide(saved.getPropriete(), saved);
        }

        notifierProposeur(saved,
                "Revenu validé",
                "Votre déclaration de revenu pour \"" + saved.getPropriete().getNom() + "\" a été validée. La distribution aux investisseurs sera prochainement effectuée.",
                TypeMessage.ANNONCE
        );

        return toResponse(saved);
    }

    @Transactional
    public RevenuResponse refuser(Long id, String motif) {
        Revenus r = revenusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + id));
        if (r.getStatut() != StatutRevenu.EN_REVIEW) {
            throw new IllegalStateException("Seuls les revenus EN_REVIEW peuvent être refusés (statut actuel : " + r.getStatut() + ")");
        }
        r.setStatut(StatutRevenu.REFUSE);
        r.setMotifRefus(motif);
        Revenus saved = revenusRepository.save(r);

        notifierProposeur(saved,
                "Revenu refusé",
                "Votre déclaration pour \"" + saved.getPropriete().getNom() + "\" a été refusée. Motif : " + motif,
                TypeMessage.AVERTISSEMENT
        );

        return toResponse(saved);
    }

    // =========================================================================
    // PHASE 10b : statut de declaration par propriete (window 1-5 + penalite)
    // =========================================================================

    /**
     * Statut de declaration TRIMESTRIELLE d'une propriete (P3 / Hugh 22/05/2026).
     * Le proprio doit declarer ses revenus une fois par trimestre, entre le 1er et
     * le 15 du 1er mois du trimestre N+1.
     */
    public StatutDeclarationResponse statutDeclarationCourant(Long proprieteId) {
        Propriete propriete = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Propriete non trouvee: id=" + proprieteId));

        LocalDate today = LocalDate.now();
        YearQuarter trimestreADeclarer = DeclarationWindowRules.trimestreADeclarer(today);
        LocalDate trimestreDebut = trimestreADeclarer.premierJour();
        LocalDate trimestreFin = trimestreADeclarer.dernierJour();
        boolean dansFenetre = DeclarationWindowRules.estDansFenetre(today);
        int joursRestants = DeclarationWindowRules.joursRestantsAvantFermeture(today);
        // V2 K (06/06/2026) : penalite retard supprimee. La valeur retournee
        // dans StatutDeclarationResponse reste a 0 pour preserver le contrat
        // API mais n'a plus d'impact metier.
        java.math.BigDecimal penaliteSi = java.math.BigDecimal.ZERO;

        // Recherche d'une declaration deja faite pour le trimestre precedent
        List<Revenus> existantes = revenusRepository.findByProprieteAndPeriode(
                proprieteId, trimestreDebut, trimestreFin);
        Revenus dejaDeclare = existantes.isEmpty() ? null
                : existantes.get(existantes.size() - 1);

        StatutDeclarationResponse.Statut statut;
        if (dejaDeclare != null) {
            statut = StatutDeclarationResponse.Statut.DECLARE;
        } else if (dansFenetre) {
            statut = StatutDeclarationResponse.Statut.DANS_FENETRE;
        } else {
            statut = StatutDeclarationResponse.Statut.EN_RETARD;
        }

        return new StatutDeclarationResponse(
                proprieteId,
                propriete.getNom(),
                trimestreADeclarer.toString(),
                statut,
                joursRestants,
                dansFenetre,
                penaliteSi,
                dejaDeclare == null ? null : dejaDeclare.getDate(),
                dejaDeclare == null ? null : dejaDeclare.getId()
        );
    }

    /**
     * V2 K (06/06/2026) : un bien est eligible a declaration uniquement s'il
     * a deja au moins un investisseur (parts vendues > 0). Sans investisseur,
     * il n'y a personne a qui distribuer le dividende -> declarer est inutile.
     */
    private boolean aAuMoinsUnInvestisseur(com.fursa.fursa_backend.model.Propriete p) {
        Integer total = p.getNombreTotalPart();
        Integer dispo = p.getPartsDisponibles();
        if (total == null || dispo == null) return false;
        return (total - dispo) > 0;
    }

    /**
     * Statuts pour toutes les proprietes proposees par un proprietaire (sa "to-do liste").
     * P8b : ignore les biens EN_CONSTRUCTION (pas de revenus possibles).
     * V2 K : ignore aussi les biens sans investisseur (declaration inutile).
     */
    public List<StatutDeclarationResponse> statutsPourProposeur(Long proposeurId) {
        return proprieteRepository.findAll().stream()
                .filter(p -> proposeurId.equals(p.getProposeurId()))
                .filter(p -> p.getStatutExploitation() != com.fursa.fursa_backend.model.enumeration.StatutExploitation.EN_CONSTRUCTION)
                .filter(this::aAuMoinsUnInvestisseur)
                .map(p -> statutDeclarationCourant(p.getId()))
                .toList();
    }

    /**
     * Statuts pour TOUTES les proprietes publiees (vue admin globale).
     * Filtrable cote appelant pour ne garder que EN_RETARD si besoin.
     *
     * P8b (Hugh 25/05/2026) : exclut les biens EN_CONSTRUCTION qui ne peuvent
     * pas encore generer de revenus.
     * V2 K (06/06/2026) : exclut aussi les biens sans investisseur.
     */
    public List<StatutDeclarationResponse> statutsTouteLaPlateforme() {
        return proprieteRepository.findAll().stream()
                .filter(p -> p.getProposeurId() != null)
                .filter(p -> p.getStatutExploitation() != com.fursa.fursa_backend.model.enumeration.StatutExploitation.EN_CONSTRUCTION)
                .filter(this::aAuMoinsUnInvestisseur)
                .map(p -> statutDeclarationCourant(p.getId()))
                .toList();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void notifierAdmins(String titre, String message, TypeMessage type) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u instanceof Investisseur)
                .forEach(u -> notificationService.envoyer((Investisseur) u, titre, message, type));
    }

    private void notifierProposeur(Revenus r, String titre, String message, TypeMessage type) {
        if (r.getProposeurId() == null) return;
        userRepository.findById(r.getProposeurId()).ifPresent(u -> {
            if (u instanceof Investisseur inv) {
                notificationService.envoyer(inv, titre, message, type);
            }
        });
    }

    private RevenuResponse toResponse(Revenus r) {
        Propriete p = r.getPropriete();
        return new RevenuResponse(
                r.getId(),
                p == null ? null : p.getId(),
                p == null ? null : p.getNom(),
                r.getDate(),
                r.getMontantTotal(),
                r.getProposeurId(),
                r.getStatut(),
                r.getMotifRefus(),
                r.getPeriodeDebut(),
                r.getPeriodeFin(),
                r.getJustificatifUrl(),
                r.getArgentRecuParFursa(),
                r.getPenaliteRetard(),
                r.getMontantDistribuable()
        );
    }

    // =========================================================================
    // PHASE 9 : justificatif (preuve de revenu) + confirmation reception FURSA
    // =========================================================================

    @Transactional
    public RevenuResponse soumettreAvecJustificatif(Long proposeurId,
                                                     SubmissionRevenuRequest req,
                                                     MultipartFile justificatif) {
        RevenuResponse response = soumettre(proposeurId, req);
        if (justificatif != null && !justificatif.isEmpty()) {
            return uploadJustificatif(proposeurId, response.id(), justificatif);
        }
        return response;
    }

    @Transactional
    public RevenuResponse uploadJustificatif(Long proposeurId, Long revenuId, MultipartFile file) {
        Revenus r = revenusRepository.findById(revenuId)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + revenuId));

        if (r.getProposeurId() == null || !r.getProposeurId().equals(proposeurId)) {
            throw new AccessDeniedException("Vous ne pouvez uploader un justificatif que pour vos propres declarations.");
        }
        if (r.getStatut() != StatutRevenu.EN_REVIEW && r.getStatut() != StatutRevenu.REFUSE) {
            throw new IllegalStateException("Justificatif modifiable uniquement quand statut EN_REVIEW ou REFUSE (actuel : " + r.getStatut() + ")");
        }

        // Supprime l'ancien si existant (cleanup)
        if (r.getJustificatifUrl() != null) {
            String oldName = extractFileName(r.getJustificatifUrl());
            if (oldName != null) {
                try { fileStorageService.delete(oldName); } catch (RuntimeException ignored) {}
            }
        }

        String stored = fileStorageService.save(file);
        r.setJustificatifUrl("/api/fichiers/" + stored);
        return toResponse(revenusRepository.save(r));
    }

    @Transactional
    public RevenuResponse marquerArgentRecu(Long revenuId, boolean recu) {
        Revenus r = revenusRepository.findById(revenuId)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + revenuId));
        if (r.getStatut() != StatutRevenu.VALIDE) {
            throw new IllegalStateException("L'argent ne peut etre confirme que sur un revenu VALIDE (actuel : " + r.getStatut() + ")");
        }
        r.setArgentRecuParFursa(recu);
        return toResponse(revenusRepository.save(r));
    }

    private String extractFileName(String url) {
        if (url == null) return null;
        int slash = url.lastIndexOf('/');
        return slash >= 0 ? url.substring(slash + 1) : url;
    }
}
