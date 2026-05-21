package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeviseRateResponse(
        String codeDevise,
        BigDecimal tauxVersUsdc,
        LocalDateTime updatedAt
) {}
