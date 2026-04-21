package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
