package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.HistoriquePrixPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriquePrixPartRepository extends JpaRepository<HistoriquePrixPart, Long> {

    /** Historique d'une propriete, du plus recent au plus ancien. */
    List<HistoriquePrixPart> findByProprieteIdOrderByCreatedAtDesc(Long proprieteId);

    /** Pour la sparkline : du plus ancien au plus recent. */
    List<HistoriquePrixPart> findByProprieteIdOrderByCreatedAtAsc(Long proprieteId);
}
