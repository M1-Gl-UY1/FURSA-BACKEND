package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.EscrowPropriete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EscrowProprieteRepository extends JpaRepository<EscrowPropriete, Long> {

    Optional<EscrowPropriete> findByProprieteId(Long proprieteId);

    boolean existsByProprieteId(Long proprieteId);
}
