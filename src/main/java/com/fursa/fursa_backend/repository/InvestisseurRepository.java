
package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Investisseur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvestisseurRepository extends JpaRepository<Investisseur, Long> {

    Optional<Investisseur> findByUserId(Long userId);
}