package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.PeriodeTrimestrielleResponse;
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
    private final RevenueLedgerService revenueLedgerService;

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

        // V2 L (06/06/2026) : le proprio doit choisir un trimestre clos. On
        // resout le trimestre cible depuis periodeDebut (ou today si non fourni)
        // et on (a) rejette les trimestres pas encore termines, (b) rejette les
        // doublons EN_REVIEW / VALIDE. Un revenu REFUSE pour le meme trimestre
        // ne bloque pas : on autorise la re-soumission.
        LocalDate today = LocalDate.now();
        LocalDate ancrageTrimestre = req.periodeDebut() != null ? req.periodeDebut() : today;
        YearQuarter trimestreCible = YearQuarter.from(ancrageTrimestre);
        LocalDate trimestreDebut = trimestreCible.premierJour();
        LocalDate trimestreFin = trimestreCible.dernierJour();

        if (trimestreFin.isAfter(today)) {
            throw new IllegalStateException(
                    "Le trimestre " + trimestreCible + " n'est pas encore termine. "
                            + "La declaration sera possible a partir du " + trimestreFin.plusDays(1) + ".");
        }

        boolean dejaActif = revenusRepository.findByProprieteAndPeriode(
                        propriete.getId(), trimestreDebut, trimestreFin).stream()
                .anyMatch(r -> r.getStatut() == StatutRevenu.EN_REVIEW
                            || r.getStatut() == StatutRevenu.VALIDE);
        if (dejaActif) {
            throw new IllegalStateException(
                    "Une declaration est deja en cours ou validee pour le trimestre "
                            + trimestreCible + ". Vous ne pouvez pas en soumettre une deuxieme.");
        }

        java.math.BigDecimal penalite = DeclarationWindowRules.penaliteApplicable(
                today, req.montantTotal());

        Revenus revenu = new Revenus();
        revenu.setPropriete(propriete);
        revenu.setMontantTotal(req.montantTotal());
        revenu.setDate(today);
        revenu.setProposeurId(proposeurId);
        revenu.setStatut(StatutRevenu.EN_REVIEW);
        // V2 L : on force les bornes du trimestre canonique (jour 1 -> dernier jour).
        revenu.setPeriodeDebut(trimestreDebut);
        revenu.setPeriodeFin(trimestreFin);
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

        // V2 P (07/06/2026) : ancrage on-chain dans le RevenueLedger (audit
        // public). No-op si ledger non configure ou propriete non tokenisee.
        ancrerRevenuOnchain(saved);

        notifierProposeur(saved,
                "Revenu validé",
                "Votre déclaration de revenu pour \"" + saved.getPropriete().getNom() + "\" a été validée. La distribution aux investisseurs sera prochainement effectuée.",
                TypeMessage.ANNONCE
        );

        return toResponse(saved);
    }

    /**
     * V2 P : ancrage on-chain d'un revenu valide.
     * - calcule sha256 du justificatif (preuve d'existence)
     * - encode le trimestre au format YYYYQ
     * - push async via RevenueLedgerService
     */
    private void ancrerRevenuOnchain(Revenus r) {
        Propriete p = r.getPropriete();
        if (p == null || p.getAdresseContrat() == null || p.getAdresseContrat().isBlank()) {
            return;
        }
        try {
            int trimestre = RevenueLedgerService.trimestreCode(
                    r.getPeriodeDebut().getYear(),
                    ((r.getPeriodeDebut().getMonthValue() - 1) / 3) + 1);
            long montantUsd = r.getMontantTotal().setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
            long dateUnix   = r.getDate().atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
            String hashHex  = hashJustificatifOrNull(r.getJustificatifUrl());

            revenueLedgerService.enregistrerRevenu(
                    p.getAdresseContrat(), trimestre, montantUsd, dateUnix,
                    hashHex, r.getId());
        } catch (Exception e) {
            // Le ledger est best-effort : un echec ici ne doit jamais bloquer
            // la validation BDD du revenu.
            // Log inline pour eviter d'introduire un logger sur la classe (deja
            // surcharge — on s'appuie sur Spring-Boot par defaut).
            org.slf4j.LoggerFactory.getLogger(RevenuService.class)
                    .error("[Ledger] Echec ancrage revenu id={} : {}",
                            r.getId(), e.getMessage(), e);
        }
    }

    private String hashJustificatifOrNull(String justificatifUrl) {
        if (justificatifUrl == null || justificatifUrl.isBlank()) return null;
        String nom = extractFileName(justificatifUrl);
        if (nom == null) return null;
        try {
            org.springframework.core.io.Resource res = fileStorageService.load(nom);
            byte[] bytes;
            try (var in = res.getInputStream()) {
                bytes = in.readAllBytes();
            }
            return RevenueLedgerService.sha256Hex(bytes);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(RevenuService.class)
                    .warn("[Ledger] hash justif indisponible ({}): {}", nom, e.getMessage());
            return null;
        }
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
    // V2 L (06/06/2026) : catalogue des trimestres declarables
    // =========================================================================

    /**
     * Liste les trimestres ouverts a declaration pour une propriete donnee,
     * du Q4 de l'annee precedente jusqu'au Q4 de l'annee courante. Chaque
     * entree porte son statut : DECLARABLE, DEJA_DECLARE ou A_VENIR.
     *
     * Securite : l'appelant doit etre le proposeur du bien OU un admin.
     */
    public List<PeriodeTrimestrielleResponse> listerPeriodesTrimestres(
            Long proprieteId, Long userId) {
        Propriete propriete = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Propriete non trouvee: id=" + proprieteId));

        boolean estAdmin = userRepository.findById(userId)
                .map(u -> u.getRole() == Role.ADMIN)
                .orElse(false);
        boolean estProposeur = propriete.getProposeurId() != null
                && propriete.getProposeurId().equals(userId);
        if (!estAdmin && !estProposeur) {
            throw new AccessDeniedException(
                    "Vous ne pouvez consulter les periodes declarables que pour vos propres biens.");
        }

        LocalDate today = LocalDate.now();
        YearQuarter trimestreCourant = YearQuarter.from(today);

        // Genere Q4 N-1, Q1 N, Q2 N, Q3 N, Q4 N -> couvre l'annee fiscale + le
        // trimestre adjacent de l'annee precedente (pour les declarations de janvier).
        java.util.List<YearQuarter> trimestres = new java.util.ArrayList<>(5);
        trimestres.add(YearQuarter.of(today.getYear() - 1, 4));
        for (int q = 1; q <= 4; q++) {
            trimestres.add(YearQuarter.of(today.getYear(), q));
        }

        return trimestres.stream()
                .map(t -> mapTrimestre(propriete.getId(), t, today, trimestreCourant))
                .toList();
    }

    private PeriodeTrimestrielleResponse mapTrimestre(
            Long proprieteId, YearQuarter t, LocalDate today, YearQuarter trimestreCourant) {
        LocalDate debut = t.premierJour();
        LocalDate fin = t.dernierJour();

        // A_VENIR : trimestre pas encore termine.
        if (fin.isAfter(today)) {
            return new PeriodeTrimestrielleResponse(
                    t.toString(), libelleTrimestre(t), debut, fin,
                    PeriodeTrimestrielleResponse.Statut.A_VENIR,
                    null, null, null, null);
        }

        // On cherche un revenu actif (EN_REVIEW ou VALIDE). Les REFUSE ne
        // bloquent pas : le proprio peut re-declarer.
        List<Revenus> revenusPeriode = revenusRepository.findByProprieteAndPeriode(
                proprieteId, debut, fin);
        Revenus actif = revenusPeriode.stream()
                .filter(r -> r.getStatut() == StatutRevenu.EN_REVIEW
                          || r.getStatut() == StatutRevenu.VALIDE)
                .reduce((a, b) -> b)  // dernier dans l'ordre d'insertion
                .orElse(null);

        if (actif != null) {
            return new PeriodeTrimestrielleResponse(
                    t.toString(), libelleTrimestre(t), debut, fin,
                    PeriodeTrimestrielleResponse.Statut.DEJA_DECLARE,
                    actif.getId(), actif.getStatut(),
                    actif.getMontantTotal(), actif.getDate());
        }

        return new PeriodeTrimestrielleResponse(
                t.toString(), libelleTrimestre(t), debut, fin,
                PeriodeTrimestrielleResponse.Statut.DECLARABLE,
                null, null, null, null);
    }

    private static String libelleTrimestre(YearQuarter t) {
        String suffixe = switch (t.quarter()) {
            case 1 -> "1er trimestre " + t.year() + " (jan-fev-mar)";
            case 2 -> "2e trimestre " + t.year() + " (avr-mai-jun)";
            case 3 -> "3e trimestre " + t.year() + " (jui-aou-sep)";
            case 4 -> "4e trimestre " + t.year() + " (oct-nov-dec)";
            default -> t.toString();
        };
        return suffixe;
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
