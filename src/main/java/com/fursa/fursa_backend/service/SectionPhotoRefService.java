package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.SectionPhotoRequest;
import com.fursa.fursa_backend.dto.SectionPhotoResponse;
import com.fursa.fursa_backend.model.Document;
import com.fursa.fursa_backend.model.SectionPhotoRef;
import com.fursa.fursa_backend.model.enumeration.SectionPhoto;
import com.fursa.fursa_backend.repository.SectionPhotoRefRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SectionPhotoRefService {

    private final SectionPhotoRefRepository repository;

    /** Liste publique (wizard) : actives uniquement, ordre d'affichage. */
    public List<SectionPhotoResponse> listerActives() {
        return repository.findByActifTrueOrderByOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    /** Liste admin : toutes (actives + inactives). */
    public List<SectionPhotoResponse> listerToutes() {
        return repository.findAllByOrderByOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public SectionPhotoResponse creer(SectionPhotoRequest req) {
        if (repository.existsByCode(req.getCode())) {
            throw new IllegalArgumentException(
                "Une section avec le code '" + req.getCode() + "' existe deja.");
        }
        SectionPhotoRef s = new SectionPhotoRef();
        s.setCode(req.getCode());
        s.setLabel(req.getLabel());
        s.setIcone(req.getIcone());
        s.setOrdre(req.getOrdre() != null ? req.getOrdre() : 100);
        s.setActif(req.getActif() != null ? req.getActif() : true);
        s.setRequise(req.getRequise() != null ? req.getRequise() : false);
        s.setCreatedAt(LocalDateTime.now());
        log.info("SectionPhoto creee : code={} label={}", s.getCode(), s.getLabel());
        return toResponse(repository.save(s));
    }

    @Transactional
    public SectionPhotoResponse modifier(Long id, SectionPhotoRequest req) {
        SectionPhotoRef s = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section introuvable : " + id));
        // Le code n'est PAS modifiable une fois cree.
        if (!s.getCode().equals(req.getCode())) {
            throw new IllegalArgumentException(
                "Le code d'une section n'est pas modifiable (creez-en une nouvelle).");
        }
        s.setLabel(req.getLabel());
        if (req.getIcone() != null) s.setIcone(req.getIcone());
        if (req.getOrdre() != null) s.setOrdre(req.getOrdre());
        if (req.getActif() != null) s.setActif(req.getActif());
        if (req.getRequise() != null) s.setRequise(req.getRequise());
        log.info("SectionPhoto {} modifiee", id);
        return toResponse(repository.save(s));
    }

    @Transactional
    public void desactiver(Long id) {
        SectionPhotoRef s = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section introuvable : " + id));
        s.setActif(false);
        repository.save(s);
        log.info("SectionPhoto {} desactivee", id);
    }

    @Transactional
    public void supprimer(Long id) {
        SectionPhotoRef s = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section introuvable : " + id));
        repository.delete(s);
        log.warn("SectionPhoto {} ({}) supprimee", id, s.getCode());
    }

    /**
     * V2 G.4 : resout le label d'un code (utilise par le mapper pour exposer
     * un label pretty dans DocumentResponse). Renvoie le code lui-meme si
     * inconnu.
     */
    public String resoudreLabel(String code) {
        if (code == null) return null;
        return repository.findByCode(code)
                .map(SectionPhotoRef::getLabel)
                .orElse(code);
    }

    /**
     * V2 G.4 : applique une section sur un document en synchronisant les
     * deux representations :
     *  - {@code sectionPhotoCode} (string, source de verite admin-configurable)
     *  - {@code sectionPhoto} (enum, conserve pour retro-compat lecture et
     *    pour la validation hardcodee FACADE/SALON dans le finalisateur)
     *
     * <p>Pour les codes historiques l'enum est aussi rempli. Pour les codes
     * custom hors enum, l'enum tombe sur AUTRE (la regle de validation
     * hardcodee FACADE/SALON n'est pas affectee : un code custom n'aurait
     * de toute facon pas a remplir une obligation FACADE ou SALON).
     */
    public void applyToDocument(Document doc, String code) {
        if (code == null || code.isBlank()) {
            doc.setSectionPhoto(null);
            doc.setSectionPhotoCode(null);
            return;
        }
        doc.setSectionPhotoCode(code);
        try {
            doc.setSectionPhoto(SectionPhoto.valueOf(code));
        } catch (IllegalArgumentException ex) {
            doc.setSectionPhoto(SectionPhoto.AUTRE);
            log.debug("Code section photo custom (hors enum) : {}", code);
        }
    }

    private SectionPhotoResponse toResponse(SectionPhotoRef s) {
        return new SectionPhotoResponse(
                s.getId(), s.getCode(), s.getLabel(),
                s.getIcone(), s.getOrdre(), s.getActif(), s.getRequise());
    }
}
