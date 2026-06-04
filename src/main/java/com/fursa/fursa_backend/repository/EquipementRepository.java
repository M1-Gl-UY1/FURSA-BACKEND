package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Equipement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipementRepository extends JpaRepository<Equipement, Long> {

    /** Liste actifs uniquement (pour exposition publique au wizard). */
    List<Equipement> findByActifTrueOrderByOrdreAsc();

    /** Liste complete pour l'admin (actifs + inactifs). */
    List<Equipement> findAllByOrderByOrdreAsc();

    Optional<Equipement> findByCode(String code);

    boolean existsByCode(String code);
}
