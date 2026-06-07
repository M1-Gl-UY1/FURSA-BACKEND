package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.BlockchainSyncTaskResponse;
import com.fursa.fursa_backend.model.BlockchainSyncTask;
import com.fursa.fursa_backend.model.enumeration.StatutSyncBlockchain;
import com.fursa.fursa_backend.repository.BlockchainSyncTaskRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * V2 R (07/06/2026) : endpoints admin pour la queue de sync blockchain.
 *
 *   - GET  /api/admin/sync-queue          liste des 50 derniers jobs
 *   - GET  /api/admin/sync-queue/stats    compteurs par statut
 *   - POST /api/admin/sync-queue/{id}/retry  remet un FAILED en PENDING (force)
 */
@RestController
@RequestMapping("/api/admin/sync-queue")
@RequiredArgsConstructor
@Tag(name = "Admin · Sync queue blockchain",
     description = "Inspection + retry manuel des jobs on-chain")
public class AdminSyncQueueController {

    private final BlockchainSyncTaskRepository repository;

    @Operation(summary = "Liste des 50 derniers jobs",
            description = "Tri decroissant par id. Inclut PENDING, SUCCESS, FAILED.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<BlockchainSyncTaskResponse>> list() {
        List<BlockchainSyncTaskResponse> items = repository.findTop50ByOrderByIdDesc()
                .stream().map(BlockchainSyncTaskResponse::from).toList();
        return ResponseEntity.ok(items);
    }

    @Operation(summary = "Compteurs par statut",
            description = "Vue synthese : combien de PENDING / SUCCESS / FAILED.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "pending", repository.countByStatus(StatutSyncBlockchain.PENDING),
                "success", repository.countByStatus(StatutSyncBlockchain.SUCCESS),
                "failed",  repository.countByStatus(StatutSyncBlockchain.FAILED)
        ));
    }

    @Operation(summary = "Forcer le retry d'un job",
            description = """
                    Repasse le job en PENDING avec attempts=0 et nextAttemptAt=NOW.
                    Le worker le reprendra a son prochain tick (max 60s).
                    Utile pour relancer un job FAILED apres correction RPC ou gas.""")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/retry")
    @Transactional
    public ResponseEntity<BlockchainSyncTaskResponse> retry(@PathVariable Long id) {
        BlockchainSyncTask task = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job introuvable : " + id));
        task.setStatus(StatutSyncBlockchain.PENDING);
        task.setAttempts(0);
        task.setNextAttemptAt(LocalDateTime.now());
        task.setLastError(null);
        BlockchainSyncTask saved = repository.save(task);
        return ResponseEntity.ok(BlockchainSyncTaskResponse.from(saved));
    }
}
