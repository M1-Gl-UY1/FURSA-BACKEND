package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10a : representation publique d'un wallet utilisateur.
 *
 * Utilise pour /api/wallet/me et /api/admin/wallets.
 * Le solde est en EUR pour MVP (multi-devise en V2).
 */
public record WalletResponse(
        Long id,
        Long userId,
        String userEmail,
        String userNom,
        String userPrenom,
        BigDecimal solde,
        String devise,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
