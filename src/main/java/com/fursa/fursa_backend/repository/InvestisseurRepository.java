package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Investisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestisseurRepository extends JpaRepository<Investisseur, Long> {
}
