package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.BrouillonPatchRequest;
import com.fursa.fursa_backend.model.Document;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.CategorieDocument;
import com.fursa.fursa_backend.model.enumeration.SectionPhoto;
import com.fursa.fursa_backend.model.enumeration.StatutCertification;
import com.fursa.fursa_backend.model.enumeration.StatutExploitation;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.model.enumeration.TypeDocument;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.DocumentRepository;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service du wizard auto-save (Phase 9, 02/06/2026).
 *
 * <p>Permet a un investisseur de creer un brouillon de propriete puis de le remplir
 * progressivement (PATCH par etape + upload medias des selection). Le bien reste en
 * statut BROUILLON, invisible publiquement, jusqu'a la finalisation qui le passe en
 * EN_REVIEW pour examen admin.
 *
 * <p>Avantages :
 * <ul>
 *   <li>L'user peut reprendre depuis un autre appareil</li>
 *   <li>Pas de perte si reseau coupe en cours d'upload</li>
 *   <li>Soumission finale legere (les medias sont deja sur le serveur)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProprieteBrouillonService {

    private final ProprieteRepository proprieteRepository;
    private final DocumentRepository documentRepository;
    private final InvestisseurRepository investisseurRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final DeviseRateService deviseRateService;

    /**
     * Cree un brouillon vide pour l'investisseur. Renvoie l'id pour que le frontend
     * puisse ensuite PATCH les etapes successives. Le bien n'est pas encore visible
     * publiquement (statut BROUILLON).
     */
    @Transactional
    public Propriete creerBrouillon(Long proposeurId) {
        Investisseur proposeur = investisseurRepository.findById(proposeurId)
                .orElseThrow(() -> new EntityNotFoundException("Investisseur introuvable : " + proposeurId));
        if (!Boolean.TRUE.equals(proposeur.getIsVerified())) {
            throw new IllegalStateException(
                "Verification KYC requise pour proposer un bien.");
        }

        Propriete p = new Propriete();
        p.setProposeurId(proposeurId);
        p.setStatut(StatutPropriete.BROUILLON);
        p.setStatutCertif(StatutCertification.NON_CERTIFIE);
        p.setCertifie(false);
        p.setSoumiseLe(LocalDateTime.now());
        // Valeurs par defaut minimales pour respecter les @NotNull JPA si presents
        p.setStatutExploitation(StatutExploitation.NEUF);
        p.setNombreTotalPart(0);
        p.setPartsDisponibles(0);
        p.setPrixUnitairePart(BigDecimal.ZERO);
        p.setRentabilitePrevue(0.0);
        p.setLocalisation("");
        p.setNom("Brouillon");
        Propriete saved = proprieteRepository.save(p);
        log.info("Brouillon cree id={} pour investisseur={}", saved.getId(), proposeurId);
        return saved;
    }

    /**
     * Update partiel : applique uniquement les champs non-null. Aucune validation stricte.
     */
    @Transactional
    public Propriete patcher(Long brouillonId, Long callerId, BrouillonPatchRequest req) {
        Propriete p = chargerBrouillonOwned(brouillonId, callerId);

        if (req.getNom() != null && !req.getNom().isBlank()) p.setNom(req.getNom());
        if (req.getPays() != null) p.setPays(req.getPays());
        if (req.getVille() != null) p.setVille(req.getVille());
        if (req.getAdressePrecise() != null) p.setAdressePrecise(req.getAdressePrecise());
        if (req.getDescription() != null) p.setDescription(req.getDescription());

        // Localisation derivee
        if (p.getPays() != null || p.getVille() != null) {
            String loc = (p.getVille() != null ? p.getVille() : "")
                    + (p.getPays() != null ? ", " + p.getPays() : "");
            loc = loc.trim();
            if (loc.startsWith(",")) loc = loc.substring(1).trim();
            p.setLocalisation(loc);
        }

        if (req.getTypeBien() != null) p.setTypeBien(req.getTypeBien());
        if (req.getNombrePieces() != null) p.setNombrePieces(req.getNombrePieces());
        if (req.getNombreChambres() != null) p.setNombreChambres(req.getNombreChambres());
        if (req.getSuperficieM2() != null) p.setSuperficieM2(req.getSuperficieM2());
        if (req.getHasPiscine() != null) p.setHasPiscine(req.getHasPiscine());
        if (req.getHasClimatisation() != null) p.setHasClimatisation(req.getHasClimatisation());
        if (req.getHasParking() != null) p.setHasParking(req.getHasParking());
        if (req.getHasAscenseur() != null) p.setHasAscenseur(req.getHasAscenseur());
        if (req.getHasJardin() != null) p.setHasJardin(req.getHasJardin());
        if (req.getHasVueMer() != null) p.setHasVueMer(req.getHasVueMer());

        if (req.getStatutExploitation() != null) p.setStatutExploitation(req.getStatutExploitation());
        if (req.getDateLivraisonPrevue() != null) p.setDateLivraisonPrevue(req.getDateLivraisonPrevue());
        if (req.getRevenuMensuelActuel() != null) p.setRevenuMensuelActuel(req.getRevenuMensuelActuel());
        if (req.getSourceRevenu() != null) p.setSourceRevenu(req.getSourceRevenu());

        if (req.getPrixVenteTotal() != null) p.setPrixVenteTotal(req.getPrixVenteTotal());
        if (req.getDeviseLocale() != null) p.setDeviseLocale(req.getDeviseLocale());
        if (req.getFractionVenduePct() != null) p.setFractionVenduePct(req.getFractionVenduePct());
        if (req.getNombreTotalPart() != null) {
            p.setNombreTotalPart(req.getNombreTotalPart());
            p.setPartsDisponibles(req.getNombreTotalPart());
        }
        if (req.getPrixUnitairePart() != null) p.setPrixUnitairePart(req.getPrixUnitairePart());
        if (req.getRentabilitePrevue() != null) p.setRentabilitePrevue(req.getRentabilitePrevue());

        return proprieteRepository.save(p);
    }

    /**
     * Ajoute des photos au brouillon (multipart). Chaque photo a sa section.
     */
    @Transactional
    public Propriete ajouterPhotos(Long brouillonId, Long callerId,
                                    List<MultipartFile> photos, List<String> sections) {
        Propriete p = chargerBrouillonOwned(brouillonId, callerId);
        if (photos == null || photos.isEmpty()) return p;

        for (int i = 0; i < photos.size(); i++) {
            MultipartFile f = photos.get(i);
            if (f == null || f.isEmpty()) continue;
            String section = (sections != null && i < sections.size()) ? sections.get(i) : null;

            String fileName = fileStorageService.save(f);
            Document doc = new Document();
            doc.setNom(f.getOriginalFilename());
            doc.setUrl(fileName);
            doc.setDateUpload(LocalDateTime.now());
            doc.setPropriete(p);
            doc.setType(TypeDocument.IMAGE);
            if (section != null) {
                try {
                    doc.setSectionPhoto(SectionPhoto.valueOf(section));
                } catch (IllegalArgumentException ignored) { /* section libre */ }
            }
            documentRepository.save(doc);
        }
        return proprieteRepository.save(p);
    }

    /**
     * Set la video de visite. Remplace l'eventuelle video precedente (file unique).
     */
    @Transactional
    public Propriete setVideo(Long brouillonId, Long callerId, MultipartFile video) {
        Propriete p = chargerBrouillonOwned(brouillonId, callerId);
        if (video == null || video.isEmpty()) return p;
        String fileName = fileStorageService.save(video);
        p.setVideoUrl("/api/fichiers/" + fileName);
        return proprieteRepository.save(p);
    }

    /**
     * Ajoute un ou plusieurs documents legaux avec leur categorie. La categorie est
     * obligatoire (parallele au fichier par index).
     */
    @Transactional
    public Propriete ajouterDocuments(Long brouillonId, Long callerId,
                                        List<MultipartFile> documents, List<String> categories) {
        Propriete p = chargerBrouillonOwned(brouillonId, callerId);
        if (documents == null || documents.isEmpty()) return p;

        for (int i = 0; i < documents.size(); i++) {
            MultipartFile f = documents.get(i);
            if (f == null || f.isEmpty()) continue;
            String cat = (categories != null && i < categories.size()) ? categories.get(i) : "AUTRE";

            String fileName = fileStorageService.save(f);
            Document doc = new Document();
            doc.setNom(f.getOriginalFilename());
            doc.setUrl(fileName);
            doc.setDateUpload(LocalDateTime.now());
            doc.setPropriete(p);
            doc.setType(f.getContentType() != null && f.getContentType().contains("pdf")
                    ? TypeDocument.PDF : TypeDocument.IMAGE);
            try {
                doc.setCategorieDocument(CategorieDocument.valueOf(cat));
            } catch (IllegalArgumentException ignored) {
                doc.setCategorieDocument(CategorieDocument.AUTRE);
            }
            documentRepository.save(doc);
        }
        return proprieteRepository.save(p);
    }

    /**
     * Supprime un media (photo, video, ou document) du brouillon.
     */
    @Transactional
    public void supprimerMedia(Long brouillonId, Long callerId, Long documentId) {
        chargerBrouillonOwned(brouillonId, callerId);
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document introuvable : " + documentId));
        if (doc.getPropriete() == null || !doc.getPropriete().getId().equals(brouillonId)) {
            throw new AccessDeniedException("Ce document n'appartient pas au brouillon.");
        }
        documentRepository.delete(doc);
    }

    /**
     * Etape finale : valide le brouillon et le bascule en EN_REVIEW. La validation
     * stricte (champs obligatoires, photos requises, docs requis selon statut) a
     * lieu ici. Si echec, le brouillon reste BROUILLON et un message d'erreur explicite
     * indique ce qu'il manque.
     */
    @Transactional
    public Propriete finaliser(Long brouillonId, Long callerId) {
        Propriete p = chargerBrouillonOwned(brouillonId, callerId);

        // --- Validation stricte des champs ---
        if (p.getNom() == null || p.getNom().isBlank() || p.getNom().equals("Brouillon")) {
            throw new IllegalStateException("Le nom du bien est obligatoire.");
        }
        if (p.getPays() == null || p.getPays().isBlank()) {
            throw new IllegalStateException("Le pays est obligatoire.");
        }
        if (p.getVille() == null || p.getVille().isBlank()) {
            throw new IllegalStateException("La ville est obligatoire.");
        }
        if (p.getTypeBien() == null) {
            throw new IllegalStateException("Le type de bien est obligatoire.");
        }
        if (p.getStatutExploitation() == null) {
            throw new IllegalStateException("Le statut d'exploitation est obligatoire.");
        }
        if (p.getNombreTotalPart() == null || p.getNombreTotalPart() <= 0) {
            throw new IllegalStateException("Le nombre de parts doit etre superieur a 0.");
        }
        if (p.getPrixUnitairePart() == null || p.getPrixUnitairePart().signum() <= 0) {
            throw new IllegalStateException("Le prix unitaire doit etre superieur a 0.");
        }
        if (p.getDeviseLocale() == null || p.getDeviseLocale().isBlank()) {
            throw new IllegalStateException("La devise locale est obligatoire.");
        }
        if (p.getRentabilitePrevue() == null) {
            throw new IllegalStateException("La rentabilite prevue est obligatoire.");
        }

        // Cross-field DEJA_RENTABLE
        if (p.getStatutExploitation() == StatutExploitation.DEJA_RENTABLE) {
            if (p.getRevenuMensuelActuel() == null || p.getRevenuMensuelActuel().signum() <= 0) {
                throw new IllegalStateException(
                    "Un bien deja rentable doit declarer un revenu mensuel actuel > 0.");
            }
            if (p.getSourceRevenu() == null) {
                throw new IllegalStateException(
                    "Un bien deja rentable doit indiquer la source des revenus.");
            }
        }

        // Cross-field EN_CONSTRUCTION
        if (p.getStatutExploitation() == StatutExploitation.EN_CONSTRUCTION
                && p.getDateLivraisonPrevue() == null) {
            throw new IllegalStateException(
                "Un bien en construction doit indiquer la date de livraison prevue.");
        }

        // --- Validation des medias ---
        List<Document> docs = p.getDocuments() == null ? List.of() : p.getDocuments();
        boolean hasFacade = docs.stream()
                .anyMatch(d -> d.getType() == TypeDocument.IMAGE
                        && d.getSectionPhoto() == SectionPhoto.FACADE);
        boolean hasSalon = docs.stream()
                .anyMatch(d -> d.getType() == TypeDocument.IMAGE
                        && d.getSectionPhoto() == SectionPhoto.SALON);
        if (!hasFacade || !hasSalon) {
            throw new IllegalStateException(
                "Photos obligatoires manquantes : facade et salon.");
        }

        if (p.getVideoUrl() == null || p.getVideoUrl().isBlank()) {
            throw new IllegalStateException("La video de visite guidee est obligatoire.");
        }

        // Documents legaux : titre foncier obligatoire + conditionnel
        boolean hasTitre = docs.stream()
                .anyMatch(d -> d.getCategorieDocument() == CategorieDocument.TITRE_FONCIER);
        if (!hasTitre) {
            throw new IllegalStateException(
                "Document obligatoire manquant : titre foncier.");
        }
        if (p.getStatutExploitation() == StatutExploitation.DEJA_RENTABLE) {
            boolean hasContrat = docs.stream().anyMatch(d ->
                    d.getCategorieDocument() == CategorieDocument.CONTRAT_GESTION
                            || d.getCategorieDocument() == CategorieDocument.CONTRAT_BAIL);
            if (!hasContrat) {
                throw new IllegalStateException(
                    "Un bien deja rentable doit avoir un contrat de gestion ou de bail.");
            }
        } else if (p.getStatutExploitation() == StatutExploitation.EN_CONSTRUCTION
                || p.getStatutExploitation() == StatutExploitation.NEUF) {
            boolean hasPermis = docs.stream()
                    .anyMatch(d -> d.getCategorieDocument() == CategorieDocument.PERMIS_CONSTRUIRE);
            if (!hasPermis) {
                throw new IllegalStateException(
                    "Un bien neuf ou en construction doit avoir un permis de construire.");
            }
        }

        // --- Conversion devise -> USD ---
        if (p.getDeviseLocale() != null && !"USD".equalsIgnoreCase(p.getDeviseLocale().trim())) {
            try {
                if (p.getPrixUnitairePart() != null && p.getPrixUnitairePart().signum() > 0) {
                    p.setPrixUnitairePart(deviseRateService.toUsd(
                            p.getPrixUnitairePart(), p.getDeviseLocale()));
                }
                if (p.getPrixVenteTotal() != null && p.getPrixVenteTotal().signum() > 0) {
                    p.setPrixVenteTotalUsd(deviseRateService.toUsd(
                            p.getPrixVenteTotal(), p.getDeviseLocale()));
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                    "Devise '" + p.getDeviseLocale() + "' non supportee.");
            }
        } else {
            p.setPrixVenteTotalUsd(p.getPrixVenteTotal());
        }

        // Prix initial = prix courant a la creation (pricing dynamique)
        if (p.getPrixInitialPart() == null) {
            p.setPrixInitialPart(p.getPrixUnitairePart());
            p.setBonusRentabiliteTotal(BigDecimal.ZERO);
            p.setBonusDemande(BigDecimal.ZERO);
        }

        // --- Bascule statut ---
        p.setStatut(StatutPropriete.EN_REVIEW);
        p.setSoumiseLe(LocalDateTime.now());
        Propriete saved = proprieteRepository.save(p);

        // Notif admins
        notificationService.notifierAdmins(
                "Nouvelle soumission de bien",
                "Le bien \"" + saved.getNom() + "\" a ete soumis pour validation.",
                TypeMessage.INFO);
        log.info("Brouillon {} finalise : passe en EN_REVIEW", brouillonId);
        return saved;
    }

    /**
     * Supprime un brouillon (et tous ses medias). Reserve au proposeur.
     * Une fois EN_REVIEW ou plus, la suppression passe par le workflow admin classique.
     */
    @Transactional
    public void supprimerBrouillon(Long brouillonId, Long callerId) {
        Propriete p = chargerBrouillonOwned(brouillonId, callerId);
        proprieteRepository.delete(p);
        log.info("Brouillon {} supprime par {}", brouillonId, callerId);
    }

    public List<Propriete> listerBrouillons(Long proposeurId) {
        return proprieteRepository.findByProposeurIdOrderByIdDesc(proposeurId)
                .stream()
                .filter(p -> p.getStatut() == StatutPropriete.BROUILLON)
                .toList();
    }

    // -------------------------------------------------------------------------

    private Propriete chargerBrouillonOwned(Long brouillonId, Long callerId) {
        Propriete p = proprieteRepository.findById(brouillonId)
                .orElseThrow(() -> new EntityNotFoundException("Brouillon introuvable : " + brouillonId));
        if (p.getProposeurId() == null || !p.getProposeurId().equals(callerId)) {
            throw new AccessDeniedException("Vous n'etes pas le proposeur de ce brouillon.");
        }
        if (p.getStatut() != StatutPropriete.BROUILLON) {
            throw new IllegalStateException(
                "Cette propriete n'est plus un brouillon (statut : " + p.getStatut() + ").");
        }
        return p;
    }
}
