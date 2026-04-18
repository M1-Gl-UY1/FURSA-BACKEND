package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProprieteRepository extends JpaRepository<Propriete, Long> {
    List<Propriete> findByStatut(StatutPropriete statut);
}
