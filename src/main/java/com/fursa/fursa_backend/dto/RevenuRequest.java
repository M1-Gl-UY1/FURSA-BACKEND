package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenuRequest(
        @NotNull Long proprieteId,
        @NotNull @Positive BigDecimal montantTotal,
        LocalDate date
) {}
