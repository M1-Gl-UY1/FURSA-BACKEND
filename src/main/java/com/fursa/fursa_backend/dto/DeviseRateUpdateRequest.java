package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DeviseRateUpdateRequest(
        @NotNull(message = "tauxVersUsdc est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "tauxVersUsdc doit etre strictement positif")
        BigDecimal tauxVersUsdc
) {}
