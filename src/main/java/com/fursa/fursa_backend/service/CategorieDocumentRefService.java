package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.CategorieDocumentRequest;
import com.fursa.fursa_backend.dto.CategorieDocumentResponse;
import com.fursa.fursa_backend.model.CategorieDocumentRef;
import com.fursa.fursa_backend.model.Document;
import com.fursa.fursa_backend.model.enumeration.CategorieDocument;
import com.fursa.fursa_backend.model.enumeration.RegleObligationDoc;
import com.fursa.fursa_backend.repository.CategorieDocumentRefRepository;
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
public class CategorieDocumentRefService {

    private final CategorieDocumentRefRepository repository;

    /** Liste publique (wizard) : actifs uniquement, ordre d'affichage. */
    public List<CategorieDocumentResponse> listerActifs() {
        return repository.findByActifTrueOrderByOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    /** Liste admin : tous (actifs + inactifs). */
    public List<CategorieDocumentResponse> listerTous() {
        return repository.findAllByOrderByOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public CategorieDocumentResponse creer(CategorieDocumentRequest req) {
        if (repository.existsByCode(req.getCode())) {
            throw new IllegalArgumentException(
                "Une categorie avec le code '" + req.getCode() + "' existe deja.");
        }
        CategorieDocumentRef c = new CategorieDocumentRef();
        c.setCode(req.getCode());
        c.setLabel(req.getLabel());
        c.setDescription(req.getDescription());
        c.setIcone(req.getIcone());
        c.setOrdre(req.getOrdre() != null ? req.getOrdre() : 100);
        c.setActif(req.getActif() != null ? req.getActif() : true);
        c.setRegleObligation(req.getRegleObligation() != null
                ? req.getRegleObligation()
                : RegleObligationDoc.OPTIONNEL);
        c.setCreatedAt(LocalDateTime.now());
        log.info("CategorieDocument cree : code={} label={}", c.getCode(), c.getLabel());
        return toResponse(repository.save(c));
    }

    @Transactional
    public CategorieDocumentResponse modifier(Long id, CategorieDocumentRequest req) {
        CategorieDocumentRef c = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categorie introuvable : " + id));
        // Le code n'est PAS modifiable une fois cree (sinon les documents
        // existants perdraient leur reference dans document.categorie_document_code).
        if (!c.getCode().equals(req.getCode())) {
            throw new IllegalArgumentException(
                "Le code d'une categorie n'est pas modifiable (creez-en une nouvelle).");
        }
        c.setLabel(req.getLabel());
        if (req.getDescription() != null) c.setDescription(req.getDescription());
        if (req.getIcone() != null) c.setIcone(req.getIcone());
        if (req.getOrdre() != null) c.setOrdre(req.getOrdre());
        if (req.getActif() != null) c.setActif(req.getActif());
        if (req.getRegleObligation() != null) c.setRegleObligation(req.getRegleObligation());
        log.info("CategorieDocument {} modifie", id);
        return toResponse(repository.save(c));
    }

    @Transactional
    public void desactiver(Long id) {
        CategorieDocumentRef c = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categorie introuvable : " + id));
        c.setActif(false);
        repository.save(c);
        log.info("CategorieDocument {} desactivee", id);
    }

    @Transactional
    public void supprimer(Long id) {
        CategorieDocumentRef c = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categorie introuvable : " + id));
        repository.delete(c);
        log.warn("CategorieDocument {} ({}) supprimee", id, c.getCode());
    }

    /**
     * V2 G.2 : resout le label d'un code (utilise par le mapper pour exposer
     * un label pretty dans DocumentResponse). Renvoie le code lui-meme si
     * inconnu (cas d'un document dont la categorie a ete supprimee).
     */
    public String resoudreLabel(String code) {
        if (code == null) return null;
        return repository.findByCode(code)
                .map(CategorieDocumentRef::getLabel)
                .orElse(code);
    }

    /**
     * V2 G.2 : applique une categorie sur un document en synchronisant les
     * deux representations :
     *  - {@code categorieDocumentCode} (string, source de verite admin-configurable)
     *  - {@code categorieDocument} (enum, conserve pour retro-compat lecture
     *    et pour la validation hardcodee dans le finalisateur)
     *
     * <p>Si {@code codeFromReq} est non-null et correspond a un code de l'enum,
     * les deux sont remplis. Si c'est un code custom hors enum, seul
     * {@code categorieDocumentCode} est rempli (l'enum tombe sur AUTRE pour
     * preserver la non-nullite si le legacy code l'attendait).
     */
    public void applyToDocument(Document doc, String code) {
        if (code == null || code.isBlank()) {
            // Defaut : AUTRE (preserve le comportement legacy).
            doc.setCategorieDocument(CategorieDocument.AUTRE);
            doc.setCategorieDocumentCode("AUTRE");
            return;
        }
        doc.setCategorieDocumentCode(code);
        try {
            doc.setCategorieDocument(CategorieDocument.valueOf(code));
        } catch (IllegalArgumentException ex) {
            // Code custom : l'enum n'en a pas connaissance. On utilise AUTRE
            // pour le legacy (preserve la non-nullite si elle existait).
            doc.setCategorieDocument(CategorieDocument.AUTRE);
            log.debug("Code categorie document custom (hors enum) : {}", code);
        }
    }

    private CategorieDocumentResponse toResponse(CategorieDocumentRef c) {
        return new CategorieDocumentResponse(
                c.getId(), c.getCode(), c.getLabel(), c.getDescription(),
                c.getIcone(), c.getOrdre(), c.getActif(),
                c.getRegleObligation());
    }
}
