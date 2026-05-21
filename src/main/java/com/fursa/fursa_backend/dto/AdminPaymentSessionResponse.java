package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaymentSessionResponse(
        Long sessionId,
        String externalId,
        Long investisseurId,
        String investisseurEmail,
        Long proprieteId,
        String proprieteNom,
        Integer nombreParts,
        BigDecimal montantFiat,
        String deviseFiat,
        BigDecimal montantUsdc,
        String providerName,
        String statut,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime confirmedAt,
        Long paiementId,
        Long transactionId,
        Long possessionId
) {}
