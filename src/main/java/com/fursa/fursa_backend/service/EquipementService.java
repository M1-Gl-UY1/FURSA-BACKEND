package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.EquipementRequest;
import com.fursa.fursa_backend.dto.EquipementResponse;
import com.fursa.fursa_backend.model.Equipement;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.repository.EquipementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EquipementService {

    private final EquipementRepository repository;

    /** Liste publique : equipements actifs uniquement (pour le wizard). */
    public List<EquipementResponse> listerActifs() {
        return repository.findByActifTrueOrderByOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    /** Liste admin : tous, incluant les inactifs. */
    public List<EquipementResponse> listerTous() {
        return repository.findAllByOrderByOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public EquipementResponse creer(EquipementRequest req) {
        if (repository.existsByCode(req.getCode())) {
            throw new IllegalArgumentException(
                "Un equipement avec le code '" + req.getCode() + "' existe deja.");
        }
        Equipement e = new Equipement();
        e.setCode(req.getCode());
        e.setLabel(req.getLabel());
        e.setIcone(req.getIcone());
        e.setOrdre(req.getOrdre() != null ? req.getOrdre() : 100);
        e.setActif(req.getActif() != null ? req.getActif() : true);
        e.setCreatedAt(LocalDateTime.now());
        log.info("Equipement cree : code={} label={}", e.getCode(), e.getLabel());
        return toResponse(repository.save(e));
    }

    @Transactional
    public EquipementResponse modifier(Long id, EquipementRequest req) {
        Equipement e = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Equipement introuvable : " + id));
        // Le code n'est PAS modifiable une fois cree (sinon les biens existants
        // perdraient leur reference dans la table de liaison).
        if (!e.getCode().equals(req.getCode())) {
            throw new IllegalArgumentException(
                "Le code d'un equipement n'est pas modifiable (creez-en un nouveau).");
        }
        e.setLabel(req.getLabel());
        if (req.getIcone() != null) e.setIcone(req.getIcone());
        if (req.getOrdre() != null) e.setOrdre(req.getOrdre());
        if (req.getActif() != null) e.setActif(req.getActif());
        log.info("Equipement {} modifie", id);
        return toResponse(repository.save(e));
    }

    @Transactional
    public void desactiver(Long id) {
        Equipement e = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Equipement introuvable : " + id));
        e.setActif(false);
        repository.save(e);
        log.info("Equipement {} desactive", id);
    }

    @Transactional
    public void supprimer(Long id) {
        // Suppression dure : autorise UNIQUEMENT si aucun bien ne reference cet
        // equipement (sinon les liens deviendraient orphelins).
        Equipement e = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Equipement introuvable : " + id));
        repository.delete(e);
        log.warn("Equipement {} ({}) supprime", id, e.getCode());
    }

    /**
     * V2 G.1 (04/06/2026) : applique une liste de codes d'equipements sur une
     * propriete, synchronise les deux representations :
     *  - les 6 booleens historiques hasPiscine/hasClimatisation/... (compat
     *    ascendante avec les anciens clients qui lisent les booleens)
     *  - le set d'entites Equipement (sources de verite pour les codes custom
     *    crees par l'admin au-dela des 6 codes connus)
     *
     * <p>Si {@code codes} est null la propriete n'est pas modifiee (no-op).
     * Si {@code codes} est vide, tous les equipements sont retires (booleens
     * remis a false, set vide).
     *
     * <p>Les codes inconnus en base sont logges en warning puis ignores (la
     * sauvegarde ne casse pas). L'admin peut creer un nouvel equipement plus
     * tard sans bloquer le wizard utilisateur.
     */
    @Transactional
    public void applyCodesToPropriete(Propriete p, List<String> codes) {
        if (codes == null) return;

        Set<String> normalised = new HashSet<>();
        for (String c : codes) {
            if (c == null) continue;
            String norm = c.trim().toUpperCase(Locale.ROOT);
            if (!norm.isEmpty()) normalised.add(norm);
        }

        // Synchronise les 6 booleens historiques (compat lecture ancienne)
        p.setHasPiscine(normalised.contains("PISCINE"));
        p.setHasClimatisation(normalised.contains("CLIMATISATION"));
        p.setHasParking(normalised.contains("PARKING"));
        p.setHasAscenseur(normalised.contains("ASCENSEUR"));
        p.setHasJardin(normalised.contains("JARDIN"));
        p.setHasVueMer(normalised.contains("VUE_MER"));

        // Synchronise le set d'entites. On modifie la collection existante
        // (clear/addAll) plutot que de la remplacer, pour ne pas casser le
        // proxy Hibernate (orphanRemoval, etc.).
        Set<Equipement> resolved = new HashSet<>();
        for (String code : normalised) {
            repository.findByCode(code).ifPresentOrElse(
                    resolved::add,
                    () -> log.warn("Code equipement inconnu ignore : {}", code)
            );
        }
        if (p.getEquipements() == null) {
            p.setEquipements(new HashSet<>());
        }
        p.getEquipements().clear();
        p.getEquipements().addAll(resolved);
    }

    private EquipementResponse toResponse(Equipement e) {
        return new EquipementResponse(
                e.getId(), e.getCode(), e.getLabel(),
                e.getIcone(), e.getOrdre(), e.getActif());
    }
}
