package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.DemandeRetrait;
import com.fursa.fursa_backend.model.enumeration.StatutDemandeRetrait;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DemandeRetraitRepository extends JpaRepository<DemandeRetrait, Long> {

    List<DemandeRetrait> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<DemandeRetrait> findByStatutOrderByCreatedAtAsc(StatutDemandeRetrait statut);
}
