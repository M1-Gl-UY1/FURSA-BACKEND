package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.EscrowTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, Long> {

    List<EscrowTransaction> findByEscrowIdOrderByCreatedAtDesc(Long escrowId);

    List<EscrowTransaction> findByInvestisseurIdOrderByCreatedAtDesc(Long investisseurId);
}
