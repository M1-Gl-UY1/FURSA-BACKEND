package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    List<Paiement> findByInvestisseurId(Long investisseurId);
    List<Paiement> findByProprieteId(Long proprieteId);
}
