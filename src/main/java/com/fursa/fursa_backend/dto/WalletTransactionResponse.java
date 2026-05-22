package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.TypeWalletTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10a : ligne d'historique d'un wallet utilisateur.
 *
 * Le montant est signe : positif = credit, negatif = debit.
 * soldeApres est le solde du wallet juste apres ce mouvement.
 */
public record WalletTransactionResponse(
        Long id,
        Long walletId,
        TypeWalletTransaction type,
        BigDecimal montant,
        BigDecimal soldeApres,
        String libelle,
        String refTable,
        Long refId,
        String metadata,
        LocalDateTime createdAt
) {}
