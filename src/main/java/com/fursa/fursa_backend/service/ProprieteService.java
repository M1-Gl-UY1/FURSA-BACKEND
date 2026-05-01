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

    @Transactional
    public Propriete soumettre(Long proposeurId, SubmissionRequest req, List<MultipartFile> fichiers) {
        Propriete p = new Propriete();
        p.setNom(req.getNom());
        p.setLocalisation(req.getLocalisation());
        p.setDescription(req.getDescription());
        p.setNombreTotalPart(req.getNombreTotalPart());
        p.setPartsDisponibles(req.getNombreTotalPart());
        p.setPrixUnitairePart(req.getPrixUnitairePart());
        p.setRentabilitePrevue(req.getRentabilitePrevue());
        p.setStatut(StatutPropriete.EN_REVIEW);
        p.setProposeurId(proposeurId);
        p.setDateCreation(LocalDate.now());
        p.setSoumiseLe(LocalDateTime.now());

        Propriete saved = proprieteRepository.save(p);
        sauvegarderFichiers(fichiers, saved);

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
}
