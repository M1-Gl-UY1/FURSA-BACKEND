package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByHashTransaction(String hashTransaction);
}
