package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Possession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PossessionRepository extends JpaRepository<Possession, Long> {
    Optional<Possession> findByInvestisseurIdAndProprieteId(Long investisseurId, Long proprieteId);
    List<Possession> findByInvestisseurId(Long investisseurId);
}
