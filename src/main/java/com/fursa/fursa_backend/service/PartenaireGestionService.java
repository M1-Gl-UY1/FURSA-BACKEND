package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.PartenaireGestionRequest;
import com.fursa.fursa_backend.dto.PartenaireGestionResponse;
import com.fursa.fursa_backend.model.PartenaireGestion;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.TypePartenaire;
import com.fursa.fursa_backend.repository.PartenaireGestionRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * P9 (Hugh 22/05/2026) : gestion des partenaires FURSA et de l'assignation
 * d'un gestionnaire locatif a un bien.
 */
@Service
@RequiredArgsConstructor
public class PartenaireGestionService {

    private final PartenaireGestionRepository repository;
    private final ProprieteRepository proprieteRepository;

    public List<PartenaireGestionResponse> listerActifs() {
        return repository.findByActifTrueOrderByNomAsc().stream()
                .map(this::toResponse).toList();
    }

    public List<PartenaireGestionResponse> listerActifsParType(TypePartenaire type) {
        return repository.findByActifTrueAndTypePartenaireOrderByNomAsc(type).stream()
                .map(this::toResponse).toList();
    }

    public List<PartenaireGestionResponse> listerTous() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public PartenaireGestionResponse creer(PartenaireGestionRequest req) {
        PartenaireGestion p = new PartenaireGestion();
        p.setNom(req.nom().trim());
        p.setTypePartenaire(req.typePartenaire());
        p.setDescription(req.description());
        p.setSiteWeb(req.siteWeb());
        p.setContactEmail(req.contactEmail());
        p.setLogoUrl(req.logoUrl());
        p.setActif(req.actif() == null ? true : req.actif());
        return toResponse(repository.save(p));
    }

    @Transactional
    public PartenaireGestionResponse modifier(Long id, PartenaireGestionRequest req) {
        PartenaireGestion p = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Partenaire non trouve: id=" + id));
        p.setNom(req.nom().trim());
        p.setTypePartenaire(req.typePartenaire());
        p.setDescription(req.description());
        p.setSiteWeb(req.siteWeb());
        p.setContactEmail(req.contactEmail());
        p.setLogoUrl(req.logoUrl());
        if (req.actif() != null) p.setActif(req.actif());
        return toResponse(repository.save(p));
    }

    @Transactional
    public void supprimer(Long id) {
        repository.deleteById(id);
    }

    /**
     * Admin : assigne un partenaire de gestion a un bien (ou le retire si null).
     * Filtre : seuls les partenaires de type GESTION_LOCATIVE peuvent etre assignes
     * comme gestionnaire d'un bien.
     */
    @Transactional
    public void assignerGestionnaire(Long proprieteId, Long partenaireId) {
        Propriete prop = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Propriete non trouvee: id=" + proprieteId));
        if (partenaireId == null) {
            prop.setGestionnaire(null);
        } else {
            PartenaireGestion partenaire = repository.findById(partenaireId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Partenaire non trouve: id=" + partenaireId));
            if (partenaire.getTypePartenaire() != TypePartenaire.GESTION_LOCATIVE) {
                throw new IllegalArgumentException(
                        "Seuls les partenaires de type GESTION_LOCATIVE peuvent gerer un bien.");
            }
            if (!Boolean.TRUE.equals(partenaire.getActif())) {
                throw new IllegalArgumentException(
                        "Le partenaire est desactive et ne peut etre assigne.");
            }
            prop.setGestionnaire(partenaire);
        }
        proprieteRepository.save(prop);
    }

    private PartenaireGestionResponse toResponse(PartenaireGestion p) {
        return new PartenaireGestionResponse(
                p.getId(),
                p.getNom(),
                p.getTypePartenaire(),
                p.getDescription(),
                p.getSiteWeb(),
                p.getContactEmail(),
                p.getLogoUrl(),
                p.getActif()
        );
    }
}
