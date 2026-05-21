package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentInitResponse(
        Long sessionId,
        String externalId,
        String widgetUrl,
        LocalDateTime expiresAt,
        BigDecimal montantFiat,
        String deviseFiat,
        BigDecimal montantUsdc,
        String providerName,
        String statut
) {}
