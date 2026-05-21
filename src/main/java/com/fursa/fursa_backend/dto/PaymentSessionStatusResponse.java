package com.fursa.fursa_backend.dto;

import java.time.LocalDateTime;

public record PaymentSessionStatusResponse(
        Long sessionId,
        String externalId,
        String statut,                  // PENDING | CONFIRMED | EXPIRED | FAILED
        String txHash,                  // vrai hash on-chain (null tant que pas CONFIRMED)
        String etherscanUrl,            // lien direct, null si pas CONFIRMED
        String errorMessage,            // null sauf FAILED
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt
) {}
