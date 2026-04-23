package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Dividende;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DividendeRepository extends JpaRepository<Dividende, Long> {
    List<Dividende> findByRevenusId(Long revenusId);
    List<Dividende> findByInvestisseurId(Long investisseurId);
}
