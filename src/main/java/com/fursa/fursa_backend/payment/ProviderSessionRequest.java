package com.fursa.fursa_backend.payment;

import java.math.BigDecimal;
import java.util.Map;

public record ProviderSessionRequest(
        String idempotencyKey,
        String investisseurEmail,
        BigDecimal montantFiat,
        String deviseFiat,
        String successCallbackUrl,
        String cancelCallbackUrl,
        Map<String, String> metadata
) {}
