package com.fursa.fursa_backend.investisseur;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fursa.fursa_backend.model.Investisseur;

public interface InvestisseurRepository extends JpaRepository<Investisseur, Long> {
    Optional<Investisseur> findByEmail(String email);
}
