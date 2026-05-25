package com.fursa.fursa_backend.service;


import com.fursa.fursa_backend.dto.ProgressionResponse;
import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.dto.SubmissionRequest;
import com.fursa.fursa_backend.mapper.ProprieteMapper;
import com.fursa.fursa_backend.model.Document;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.model.enumeration.TypeDocument;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.DocumentRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ProprieteService {

    private final ProprieteRepository proprieteRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final ProprieteMapper proprieteMapper;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final DeviseRateService deviseRateService;

    @Transactional
    public Propriete creerPropriete(ProprieteRequest request, List<MultipartFile> fichiers) {

        Propriete propriete = proprieteMapper.toEntity(request);
        propriete.setDateCreation(LocalDate.now());
        Propriete savedProp = proprieteRepository.save(propriete);

        sauvegarderFichiers(fichiers, savedProp);

        // Recharge avec les documents pour la réponse
        return proprieteRepository.findById(savedProp.getId()).orElseThrow();
    }

    @Transactional
    public Propriete modifierPropriete(Long id, ProprieteRequest request, List<MultipartFile> fichiers) {

        Propriete propriete = proprieteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + id));

        propriete.setNom(request.getNom());
        propriete.setLocalisation(request.getLocalisation());
        propriete.setDescription(request.getDescription());
        propriete.setNombreTotalPart(request.getNombreTotalPart());
        propriete.setPrixUnitairePart(request.getPrixUnitairePart());
        propriete.setStatut(request.getStatut());
        propriete.setRentabilitePrevue(request.getRentabilitePrevue());

        sauvegarderFichiers(fichiers, propriete);

        return proprieteRepository.save(propriete);
    }

    public List<Propriete> listerTout() {
        return proprieteRepository.findAll();
    }

    public Propriete detail(Long id) {
        return proprieteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + id));
    }

    @Transactional
    public Propriete publier(Long id) {
        Propriete propriete = proprieteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + id));
        if (propriete.getStatut() == StatutPropriete.PUBLIEE) {
            return propriete;
        }
        propriete.setStatut(StatutPropriete.PUBLIEE);
        Propriete saved = proprieteRepository.save(propriete);

        if (saved.getProposeurId() != null) {
            userRepository.findById(saved.getProposeurId()).ifPresent(u -> {
                if (u instanceof Investisseur inv) {
                    notificationService.envoyer(
                            inv,
                            "Propriété publiée",
                            "Votre bien \"" + saved.getNom() + "\" est maintenant en vente sur la plateforme.",
                            TypeMessage.ANNONCE
                    );
                }
            });
        }
        return saved;
    }

    public ProgressionResponse progression(Long id) {
        Propriete p = proprieteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + id));
        int total = p.getNombreTotalPart() == null ? 0 : p.getNombreTotalPart();
        int dispo = p.getPartsDisponibles() == null ? 0 : p.getPartsDisponibles();
        int vendues = total - dispo;
        double pct = total == 0 ? 0.0 : Math.round(((double) vendues / total) * 10000.0) / 100.0;
        return new ProgressionResponse(p.getId(), total, vendues, dispo, pct);
    }

    @Transactional
    public void supprimer(Long id) {
        Propriete propriete = proprieteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + id));

        if (propriete.getDocuments() != null) {
            propriete.getDocuments().forEach(doc ->
                fileStorageService.delete(doc.getUrl())
            );
        }

        proprieteRepository.deleteById(id);
    }

    // =========================================================================
    // PHASE 7 : workflow soumission propriétaire
    // =========================================================================

    /**
     * Overload pour compatibilite : l'ancien appel sans video / photos par section.
     */
    @Transactional
    public Propriete soumettre(Long proposeurId, SubmissionRequest req, List<MultipartFile> fichiers) {
        return soumettre(proposeurId, req, fichiers, null, null, null, null);
    }

    @Transactional
    public Propriete soumettre(Long proposeurId, SubmissionRequest req,
                                List<MultipartFile> filesLegacy,
                                MultipartFile video,
                                List<MultipartFile> photos,
                                List<String> photoSections,
                                List<MultipartFile> documents) {
        // P1 (Hugh 22/05/2026) : validation cross-field pour les biens DEJA_RENTABLE.
        if (req.getStatutExploitation() == com.fursa.fursa_backend.model.enumeration.StatutExploitation.DEJA_RENTABLE) {
            if (req.getRevenuMensuelActuel() == null || req.getRevenuMensuelActuel().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Un bien deja rentable doit declarer un revenu mensuel actuel > 0.");
            }
            if (req.getSourceRevenu() == null) {
                throw new IllegalArgumentException(
                        "Un bien deja rentable doit indiquer la source des revenus (BAIL / AIRBNB / AUTRE).");
            }
        }

        // Localisation derivee si non fournie explicitement (compat ascendante).
        String localisation = req.getLocalisation();
        if (localisation == null || localisation.isBlank()) {
            localisation = (req.getVille() != null ? req.getVille() : "")
                    + (req.getPays() != null ? ", " + req.getPays() : "");
            localisation = localisation.trim();
            if (localisation.startsWith(",")) localisation = localisation.substring(1).trim();
        }

        // P5 (Hugh 22/05/2026) : conversion devise locale -> USD.
        // Le wizard frontend calcule prixUnitairePart en DEVISE LOCALE (a partir de
        // prixVenteTotal / fraction / nombreTotalPart). Le backend convertit en USD
        // pour stocker un prix unifie sur toute la plateforme. Le prixVenteTotal
        // d'origine reste en devise locale pour info.
        java.math.BigDecimal prixUnitaireUsd = req.getPrixUnitairePart();
        java.math.BigDecimal prixVenteTotalUsd = req.getPrixVenteTotal();
        String devise = req.getDeviseLocale();
        if (devise != null && !"USD".equalsIgnoreCase(devise.trim())) {
            try {
                if (req.getPrixUnitairePart() != null && req.getPrixUnitairePart().signum() > 0) {
                    prixUnitaireUsd = deviseRateService.toUsd(req.getPrixUnitairePart(), devise);
                }
                if (req.getPrixVenteTotal() != null && req.getPrixVenteTotal().signum() > 0) {
                    prixVenteTotalUsd = deviseRateService.toUsd(req.getPrixVenteTotal(), devise);
                }
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Devise '" + devise + "' non supportee. Configurez son taux via /admin/devises.");
            }
        }

        Propriete p = new Propriete();
        p.setNom(req.getNom());
        p.setLocalisation(localisation);
        p.setDescription(req.getDescription());
        p.setNombreTotalPart(req.getNombreTotalPart());
        p.setPartsDisponibles(req.getNombreTotalPart());
        p.setPrixUnitairePart(prixUnitaireUsd);
        p.setRentabilitePrevue(req.getRentabilitePrevue());
        p.setStatut(StatutPropriete.EN_REVIEW);
        p.setProposeurId(proposeurId);
        p.setDateCreation(LocalDate.now());
        p.setSoumiseLe(LocalDateTime.now());

        // P1 (Hugh 22/05/2026) : nouveaux champs structures.
        p.setPays(req.getPays());
        p.setVille(req.getVille());
        p.setAdressePrecise(req.getAdressePrecise());
        p.setTypeBien(req.getTypeBien());
        p.setNombrePieces(req.getNombrePieces());
        p.setNombreChambres(req.getNombreChambres());
        p.setSuperficieM2(req.getSuperficieM2());
        p.setHasPiscine(Boolean.TRUE.equals(req.getHasPiscine()));
        p.setHasClimatisation(Boolean.TRUE.equals(req.getHasClimatisation()));
        p.setHasParking(Boolean.TRUE.equals(req.getHasParking()));
        p.setHasAscenseur(Boolean.TRUE.equals(req.getHasAscenseur()));
        p.setHasJardin(Boolean.TRUE.equals(req.getHasJardin()));
        p.setHasVueMer(Boolean.TRUE.equals(req.getHasVueMer()));
        p.setStatutExploitation(req.getStatutExploitation());
        p.setRevenuMensuelActuel(req.getRevenuMensuelActuel());
        p.setSourceRevenu(req.getSourceRevenu());
        p.setPrixVenteTotal(req.getPrixVenteTotal());
        p.setDeviseLocale(req.getDeviseLocale());
        p.setPrixVenteTotalUsd(prixVenteTotalUsd);
        p.setFractionVenduePct(req.getFractionVenduePct() == null ? 100 : req.getFractionVenduePct());
        p.setVideoUrl(req.getVideoUrl());
        p.setCertifie(false);

        Propriete saved = proprieteRepository.save(p);

        // P1 : sauvegarde des fichiers selon leur type.
        // 1. Legacy : ancien champ "files" (toutes en AUTRE pour ne rien casser).
        sauvegarderPhotos(filesLegacy, saved,
                java.util.Collections.nCopies(filesLegacy == null ? 0 : filesLegacy.size(), "AUTRE"));
        // 2. Photos structurees par section.
        sauvegarderPhotos(photos, saved, photoSections);
        // 3. Video de visite guidee (Hugh exige).
        sauvegarderVideo(video, saved);
        // 4. Documents legaux (PDFs). Stockes mais NON marques certifies a la creation
        //    (la certification est une etape separee Phase 7-bis demandee par Hugh).
        sauvegarderDocuments(documents, saved);

        notifierAdmins(
                "Nouvelle soumission de bien",
                "Le bien \"" + saved.getNom() + "\" a été soumis pour validation.",
                TypeMessage.INFO
        );

        return proprieteRepository.findById(saved.getId()).orElseThrow();
    }

    public List<Propriete> listerProposeesPar(Long proposeurId) {
        return proprieteRepository.findByProposeurIdOrderByIdDesc(proposeurId);
    }

    @Transactional
    public Propriete approuver(Long id) {
        Propriete p = proprieteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + id));
        if (p.getStatut() != StatutPropriete.EN_REVIEW) {
            throw new IllegalStateException("Seules les propriétés en EN_REVIEW peuvent être approuvées (statut actuel : " + p.getStatut() + ")");
        }
        p.setStatut(StatutPropriete.ACCEPTEE);
        p.setMotifRefus(null);
        Propriete saved = proprieteRepository.save(p);

        if (saved.getProposeurId() != null) {
            userRepository.findById(saved.getProposeurId()).ifPresent(u -> {
                if (u instanceof Investisseur inv) {
                    notificationService.envoyer(
                            inv,
                            "Propriété acceptée",
                            "Votre bien \"" + saved.getNom() + "\" a été validé. Il sera publié prochainement.",
                            TypeMessage.ANNONCE
                    );
                }
            });
        }
        return saved;
    }

    @Transactional
    public Propriete refuser(Long id, String motif) {
        Propriete p = proprieteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + id));
        if (p.getStatut() != StatutPropriete.EN_REVIEW) {
            throw new IllegalStateException("Seules les propriétés en EN_REVIEW peuvent être refusées (statut actuel : " + p.getStatut() + ")");
        }
        p.setStatut(StatutPropriete.REFUSEE);
        p.setMotifRefus(motif);
        Propriete saved = proprieteRepository.save(p);

        if (saved.getProposeurId() != null) {
            userRepository.findById(saved.getProposeurId()).ifPresent(u -> {
                if (u instanceof Investisseur inv) {
                    notificationService.envoyer(
                            inv,
                            "Propriété refusée",
                            "Votre bien \"" + saved.getNom() + "\" a été refusé. Motif : " + motif,
                            TypeMessage.AVERTISSEMENT
                    );
                }
            });
        }
        return saved;
    }

    // =========================================================================
    // Phase Certification (Hugh 22/05/2026) : etape post-creation separee
    // =========================================================================

    /**
     * Upload d'un document legal pour certification (titre foncier, contrat, etc.).
     * Le proprietaire DOIT etre proposeur du bien. Le document est stocke avec
     * sectionPhoto=null (= document legal, pas une photo).
     */
    @Transactional
    public Propriete uploadDocumentCertification(Long proposeurId, Long proprieteId,
                                                  List<MultipartFile> documents) {
        Propriete p = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + proprieteId));
        if (p.getProposeurId() == null || !p.getProposeurId().equals(proposeurId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Vous ne pouvez uploader des documents que pour vos propres biens.");
        }
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Au moins un document est requis.");
        }
        sauvegarderDocuments(documents, p);
        return proprieteRepository.findById(p.getId()).orElseThrow();
    }

    /**
     * Le proprietaire soumet sa demande de certification. Pre-requis : au moins
     * un document legal uploade (verifie en service).
     */
    @Transactional
    public Propriete soumettreCertification(Long proposeurId, Long proprieteId) {
        Propriete p = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + proprieteId));
        if (p.getProposeurId() == null || !p.getProposeurId().equals(proposeurId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Vous ne pouvez certifier que vos propres biens.");
        }
        if (p.getStatutCertif() == com.fursa.fursa_backend.model.enumeration.StatutCertification.CERTIFIE) {
            throw new IllegalStateException("Ce bien est deja certifie.");
        }
        if (p.getStatutCertif() == com.fursa.fursa_backend.model.enumeration.StatutCertification.EN_REVIEW) {
            throw new IllegalStateException("Une demande de certification est deja en cours d'examen.");
        }
        // Verifier qu'au moins un doc legal a ete uploade (sectionPhoto null = doc legal)
        long nbDocsLegaux = p.getDocuments() == null ? 0 :
                p.getDocuments().stream()
                        .filter(d -> d.getSectionPhoto() == null
                                && d.getType() == com.fursa.fursa_backend.model.enumeration.TypeDocument.PDF)
                        .count();
        if (nbDocsLegaux == 0) {
            throw new IllegalStateException(
                    "Aucun document legal trouve. Uploadez au moins un PDF (titre foncier, contrat...) avant de soumettre.");
        }

        p.setStatutCertif(com.fursa.fursa_backend.model.enumeration.StatutCertification.EN_REVIEW);
        p.setCertifSoumiseLe(LocalDateTime.now());
        p.setCertifMotifRefus(null);
        Propriete saved = proprieteRepository.save(p);

        notifierAdmins(
                "Demande de certification",
                "Le bien \"" + p.getNom() + "\" a soumis " + nbDocsLegaux
                        + " document(s) legal(aux) pour certification.",
                TypeMessage.INFO);

        return saved;
    }

    /**
     * Admin approuve la certification. Le bien devient achetable.
     */
    @Transactional
    public Propriete approuverCertification(Long proprieteId) {
        Propriete p = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + proprieteId));
        if (p.getStatutCertif() != com.fursa.fursa_backend.model.enumeration.StatutCertification.EN_REVIEW) {
            throw new IllegalStateException(
                    "Seules les demandes EN_REVIEW peuvent etre approuvees (actuel : " + p.getStatutCertif() + ")");
        }
        p.setStatutCertif(com.fursa.fursa_backend.model.enumeration.StatutCertification.CERTIFIE);
        p.setCertifie(true);
        p.setCertifieLe(LocalDateTime.now());
        p.setCertifMotifRefus(null);
        Propriete saved = proprieteRepository.save(p);

        if (saved.getProposeurId() != null) {
            userRepository.findById(saved.getProposeurId()).ifPresent(u -> {
                if (u instanceof Investisseur inv) {
                    notificationService.envoyer(
                            inv,
                            "Votre bien est certifie !",
                            "Felicitations. Le bien \"" + saved.getNom() + "\" est certifie. "
                                    + "Les investisseurs peuvent desormais acheter des parts.",
                            TypeMessage.ANNONCE);
                }
            });
        }
        return saved;
    }

    /**
     * Admin refuse la certification (documents incomplets / faux / autre).
     * Le proprio peut re-uploader puis re-soumettre.
     */
    @Transactional
    public Propriete refuserCertification(Long proprieteId, String motif) {
        if (motif == null || motif.trim().length() < 10) {
            throw new IllegalArgumentException("Motif obligatoire (min 10 caracteres).");
        }
        Propriete p = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + proprieteId));
        if (p.getStatutCertif() != com.fursa.fursa_backend.model.enumeration.StatutCertification.EN_REVIEW) {
            throw new IllegalStateException(
                    "Seules les demandes EN_REVIEW peuvent etre refusees (actuel : " + p.getStatutCertif() + ")");
        }
        p.setStatutCertif(com.fursa.fursa_backend.model.enumeration.StatutCertification.REFUSEE);
        p.setCertifMotifRefus(motif.trim());
        Propriete saved = proprieteRepository.save(p);

        if (saved.getProposeurId() != null) {
            userRepository.findById(saved.getProposeurId()).ifPresent(u -> {
                if (u instanceof Investisseur inv) {
                    notificationService.envoyer(
                            inv,
                            "Certification refusee",
                            "La certification de \"" + saved.getNom() + "\" a ete refusee. Motif : "
                                    + motif + ". Vous pouvez re-uploader vos documents et re-soumettre.",
                            TypeMessage.AVERTISSEMENT);
                }
            });
        }
        return saved;
    }

    /** Liste des biens en attente de certification (admin). */
    public List<Propriete> listerEnAttenteCertification() {
        return proprieteRepository.findAll().stream()
                .filter(p -> p.getStatutCertif()
                        == com.fursa.fursa_backend.model.enumeration.StatutCertification.EN_REVIEW)
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

    private void sauvegarderFichiers(List<MultipartFile> fichiers, Propriete propriete) {
        if (fichiers == null || fichiers.isEmpty()) return;

        for (MultipartFile f : fichiers) {
            String nomFichier = fileStorageService.save(f);

            Document doc = new Document();
            doc.setNom(f.getOriginalFilename());
            doc.setUrl(nomFichier);
            doc.setDateUpload(LocalDateTime.now());
            doc.setPropriete(propriete);
            doc.setType(
                f.getContentType() != null && f.getContentType().contains("pdf")
                    ? TypeDocument.PDF
                    : TypeDocument.IMAGE
            );
            documentRepository.save(doc);
        }
    }

    // =========================================================================
    // P1 (Hugh 22/05/2026) : helpers fichiers structures
    // =========================================================================

    /**
     * Sauvegarde une liste de photos avec leur section associee (FACADE, SALON, etc.).
     * Les indices de sections doivent correspondre aux indices des photos (zip parallele).
     * Si la liste sections est plus courte ou null, le reste est marque AUTRE.
     */
    private void sauvegarderPhotos(List<MultipartFile> photos, Propriete propriete,
                                    List<String> sections) {
        if (photos == null || photos.isEmpty()) return;
        for (int i = 0; i < photos.size(); i++) {
            MultipartFile f = photos.get(i);
            if (f == null || f.isEmpty()) continue;
            String nomFichier = fileStorageService.save(f);

            com.fursa.fursa_backend.model.enumeration.SectionPhoto section;
            try {
                String code = sections != null && i < sections.size() ? sections.get(i) : "AUTRE";
                section = com.fursa.fursa_backend.model.enumeration.SectionPhoto.valueOf(code);
            } catch (IllegalArgumentException ex) {
                section = com.fursa.fursa_backend.model.enumeration.SectionPhoto.AUTRE;
            }

            Document doc = new Document();
            doc.setNom(f.getOriginalFilename());
            doc.setUrl(nomFichier);
            doc.setDateUpload(LocalDateTime.now());
            doc.setPropriete(propriete);
            doc.setType(TypeDocument.IMAGE);
            doc.setSectionPhoto(section);
            documentRepository.save(doc);
        }
    }

    /**
     * Sauvegarde la video de visite guidee. Met a jour propriete.videoUrl.
     */
    private void sauvegarderVideo(MultipartFile video, Propriete propriete) {
        if (video == null || video.isEmpty()) return;
        String nomFichier = fileStorageService.save(video);
        propriete.setVideoUrl("/api/fichiers/" + nomFichier);
        proprieteRepository.save(propriete);

        // Trace egalement en Document (audit) pour retrouver la video.
        Document doc = new Document();
        doc.setNom(video.getOriginalFilename());
        doc.setUrl(nomFichier);
        doc.setDateUpload(LocalDateTime.now());
        doc.setPropriete(propriete);
        doc.setType(TypeDocument.IMAGE); // pas d'enum VIDEO pour l'instant, on garde IMAGE faute de mieux
        documentRepository.save(doc);
    }

    /**
     * Sauvegarde les documents legaux (PDFs titre foncier, contrats, etc.).
     * Phase 7-bis : ces documents serviront a la certification du bien (validation
     * admin separee). Ils sont stockes mais propriete.certifie reste false.
     */
    private void sauvegarderDocuments(List<MultipartFile> documents, Propriete propriete) {
        if (documents == null || documents.isEmpty()) return;
        for (MultipartFile f : documents) {
            if (f == null || f.isEmpty()) continue;
            String nomFichier = fileStorageService.save(f);

            Document doc = new Document();
            doc.setNom(f.getOriginalFilename());
            doc.setUrl(nomFichier);
            doc.setDateUpload(LocalDateTime.now());
            doc.setPropriete(propriete);
            doc.setType(
                f.getContentType() != null && f.getContentType().contains("pdf")
                    ? TypeDocument.PDF
                    : TypeDocument.IMAGE
            );
            // sectionPhoto null = ce n'est pas une photo, c'est un document legal
            documentRepository.save(doc);
        }
    }
}
