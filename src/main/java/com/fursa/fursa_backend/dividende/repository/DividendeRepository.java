package com.fursa.fursa_backend.dividende.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;

public interface DividendeRepository extends JpaRepository<Dividende, Long>{
     // Récupère tous les dividendes d'un revenu avec un statut donné
    List<Dividende> findByRevenusIdAndStatut(Long revenusId, StatutPaiement statut);

}
