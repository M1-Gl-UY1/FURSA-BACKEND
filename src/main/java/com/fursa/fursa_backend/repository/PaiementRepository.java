package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Paiement;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    List<Paiement> findByInvestisseur(Investisseur investisseur);

    List<Paiement> findByStatut(StatutPaiement statut);

    List<Paiement> findByProprieteId(Long proprieteId);
}