package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.KycSubmission;
import com.fursa.fursa_backend.model.enumeration.StatutKyc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KycSubmissionRepository extends JpaRepository<KycSubmission, Long> {

    /** La derniere soumission d'un investisseur (utile pour le statut courant). */
    Optional<KycSubmission> findFirstByInvestisseur_IdOrderBySubmittedAtDesc(Long investisseurId);

    /** Historique complet d'un investisseur. */
    List<KycSubmission> findByInvestisseur_IdOrderBySubmittedAtDesc(Long investisseurId);

    /** Liste admin filtree par statut, ordre antichronologique. */
    List<KycSubmission> findByStatutOrderBySubmittedAtDesc(StatutKyc statut);

    long countByStatut(StatutKyc statut);
}
