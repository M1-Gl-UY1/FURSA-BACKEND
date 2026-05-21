package com.fursa.fursa_backend.payment;

import java.math.BigDecimal;

public record WebhookEvent(
        WebhookEventType type,
        String externalSessionId,
        BigDecimal amountReceived,
        String currency,
        String providerTxHash,
        String errorMessage
) {}
