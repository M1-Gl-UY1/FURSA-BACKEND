package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.DeviseRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviseRateRepository extends JpaRepository<DeviseRate, String> {
}
