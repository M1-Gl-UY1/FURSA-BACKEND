package com.fursa.fursa_backend.dividend_Calculation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fursa.fursa_backend.model.Possession;

public interface PossessionRepository extends JpaRepository<Possession, Long> {
    List<Possession> findByProprieteId(Long id);

}
