package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.TypeBienRequest;
import com.fursa.fursa_backend.dto.TypeBienResponse;
import com.fursa.fursa_backend.model.TypeBienRef;
import com.fursa.fursa_backend.repository.TypeBienRefRepository;
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
public class TypeBienRefService {

    private final TypeBienRefRepository repository;

    /** Liste publique (wizard) : actifs uniquement, ordre d'affichage. */
    public List<TypeBienResponse> listerActifs() {
        return repository.findByActifTrueOrderByOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    /** Liste admin : tous (actifs + inactifs). */
    public List<TypeBienResponse> listerTous() {
        return repository.findAllByOrderByOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public TypeBienResponse creer(TypeBienRequest req) {
        if (repository.existsByCode(req.getCode())) {
            throw new IllegalArgumentException(
                "Un type de bien avec le code '" + req.getCode() + "' existe deja.");
        }
        TypeBienRef t = new TypeBienRef();
        t.setCode(req.getCode());
        t.setLabel(req.getLabel());
        t.setIcone(req.getIcone());
        t.setOrdre(req.getOrdre() != null ? req.getOrdre() : 100);
        t.setActif(req.getActif() != null ? req.getActif() : true);
        t.setExigeChambres(req.getExigeChambres() != null ? req.getExigeChambres() : true);
        t.setCreatedAt(LocalDateTime.now());
        log.info("TypeBien cree : code={} label={}", t.getCode(), t.getLabel());
        return toResponse(repository.save(t));
    }

    @Transactional
    public TypeBienResponse modifier(Long id, TypeBienRequest req) {
        TypeBienRef t = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Type de bien introuvable : " + id));
        // Le code n'est PAS modifiable une fois cree (sinon les biens existants
        // perdraient leur reference dans propriete.type_bien_code).
        if (!t.getCode().equals(req.getCode())) {
            throw new IllegalArgumentException(
                "Le code d'un type de bien n'est pas modifiable (creez-en un nouveau).");
        }
        t.setLabel(req.getLabel());
        if (req.getIcone() != null) t.setIcone(req.getIcone());
        if (req.getOrdre() != null) t.setOrdre(req.getOrdre());
        if (req.getActif() != null) t.setActif(req.getActif());
        if (req.getExigeChambres() != null) t.setExigeChambres(req.getExigeChambres());
        log.info("TypeBien {} modifie", id);
        return toResponse(repository.save(t));
    }

    @Transactional
    public void desactiver(Long id) {
        TypeBienRef t = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Type de bien introuvable : " + id));
        t.setActif(false);
        repository.save(t);
        log.info("TypeBien {} desactive", id);
    }

    @Transactional
    public void supprimer(Long id) {
        TypeBienRef t = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Type de bien introuvable : " + id));
        repository.delete(t);
        log.warn("TypeBien {} ({}) supprime", id, t.getCode());
    }

    /**
     * Resout le label d'un code (utilise par le mapper pour exposer un label
     * pretty dans ProprieteResponse). Renvoie le code lui-meme si inconnu
     * (cas d'un bien dont le type a ete supprime entre-temps).
     */
    public String resoudreLabel(String code) {
        if (code == null) return null;
        return repository.findByCode(code)
                .map(TypeBienRef::getLabel)
                .orElse(code);
    }

    private TypeBienResponse toResponse(TypeBienRef t) {
        return new TypeBienResponse(
                t.getId(), t.getCode(), t.getLabel(),
                t.getIcone(), t.getOrdre(), t.getActif(),
                t.getExigeChambres());
    }
}
