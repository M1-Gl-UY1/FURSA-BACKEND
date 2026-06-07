package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.BlockchainSyncTask;
import com.fursa.fursa_backend.model.enumeration.StatutSyncBlockchain;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BlockchainSyncTaskRepository extends JpaRepository<BlockchainSyncTask, Long> {

    /**
     * Jobs prets a executer : PENDING ET (next_attempt_at IS NULL OR next_attempt_at <= now).
     * Tri FIFO pour traiter dans l'ordre d'arrivee.
     */
    @Query("SELECT t FROM BlockchainSyncTask t "
         + "WHERE t.status = :status "
         + "AND (t.nextAttemptAt IS NULL OR t.nextAttemptAt <= :now) "
         + "ORDER BY t.id ASC")
    List<BlockchainSyncTask> findReady(
            @Param("status") StatutSyncBlockchain status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    List<BlockchainSyncTask> findTop50ByOrderByIdDesc();

    long countByStatus(StatutSyncBlockchain status);
}
