package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.WalletTransaction;
import com.fursa.fursa_backend.model.enumeration.TypeWalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    @Query("SELECT t FROM WalletTransaction t WHERE t.wallet.id = :walletId " +
            "AND (:type IS NULL OR t.type = :type) " +
            "AND (:from IS NULL OR t.createdAt >= :from) " +
            "AND (:to IS NULL OR t.createdAt <= :to) " +
            "ORDER BY t.createdAt DESC")
    List<WalletTransaction> findFiltered(
            @Param("walletId") Long walletId,
            @Param("type") TypeWalletTransaction type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.montant), 0) FROM WalletTransaction t WHERE t.wallet.id = :walletId")
    BigDecimal sumMontantByWalletId(@Param("walletId") Long walletId);
}
