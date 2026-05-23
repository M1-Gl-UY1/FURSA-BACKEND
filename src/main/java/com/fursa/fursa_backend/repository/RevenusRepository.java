package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Revenus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RevenusRepository extends JpaRepository<Revenus, Long> {
    List<Revenus> findByProprieteId(Long proprieteId);

    /** Phase 8 : revenus déclarés par un investisseur sur ses biens. */
    List<Revenus> findByProposeurIdOrderByIdDesc(Long proposeurId);

    /**
     * Phase 10b : revenus declares sur une propriete dont la periode (debut OU date de soumission)
     * tombe dans la fenetre [from, to]. Sert a verifier "deja declare ce mois ?".
     */
    @Query("SELECT r FROM Revenus r WHERE r.propriete.id = :proprieteId " +
            "AND ((r.periodeDebut IS NOT NULL AND r.periodeDebut BETWEEN :from AND :to) " +
            "     OR (r.periodeDebut IS NULL AND r.date BETWEEN :from AND :to))")
    List<Revenus> findByProprieteAndPeriode(
            @Param("proprieteId") Long proprieteId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
