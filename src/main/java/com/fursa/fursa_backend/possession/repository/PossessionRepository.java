package com.fursa.fursa_backend.possession.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;

public interface PossessionRepository extends JpaRepository<Possession, Long> {
    List<Possession> findByProprieteId(Long id);
    Optional<Possession> findByInvestisseurAndPropriete(
        Investisseur investisseur, Propriete propriete
    );

}
