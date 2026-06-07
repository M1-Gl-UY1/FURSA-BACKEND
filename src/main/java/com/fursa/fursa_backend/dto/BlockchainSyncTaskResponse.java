package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.BlockchainSyncTask;
import com.fursa.fursa_backend.model.enumeration.StatutSyncBlockchain;
import com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain;

import java.time.LocalDateTime;

/**
 * V2 R (07/06/2026) : vue admin d'un job de la queue blockchain.
 *
 * On expose le payload brut (string JSON) plutot que de le re-parser :
 * c'est l'admin qui debug, il a besoin de voir tel quel.
 */
public record BlockchainSyncTaskResponse(
        Long id,
        TypeSyncBlockchain type,
        Long refId,
        String payload,
        StatutSyncBlockchain status,
        Integer attempts,
        LocalDateTime nextAttemptAt,
        LocalDateTime lastAttemptAt,
        String lastError,
        String txHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BlockchainSyncTaskResponse from(BlockchainSyncTask t) {
        return new BlockchainSyncTaskResponse(
                t.getId(),
                t.getType(),
                t.getRefId(),
                t.getPayload(),
                t.getStatus(),
                t.getAttempts(),
                t.getNextAttemptAt(),
                t.getLastAttemptAt(),
                t.getLastError(),
                t.getTxHash(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
