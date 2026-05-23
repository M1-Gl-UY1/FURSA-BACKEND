package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.TypeEscrowTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10c : ligne d'historique d'un escrow propriete.
 */
public record EscrowTransactionResponse(
        Long id,
        Long escrowId,
        TypeEscrowTransaction type,
        BigDecimal montant,
        BigDecimal soldeApres,
        Long investisseurId,
        String refTable,
        Long refId,
        String libelle,
        String metadata,
        LocalDateTime createdAt
) {}
