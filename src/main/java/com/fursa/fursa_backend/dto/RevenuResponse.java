package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenuResponse(
        Long id,
        Long proprieteId,
        String proprieteNom,
        LocalDate date,
        BigDecimal montantTotal
) {}
