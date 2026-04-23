package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Revenus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RevenusRepository extends JpaRepository<Revenus, Long> {
    List<Revenus> findByProprieteId(Long proprieteId);
}
