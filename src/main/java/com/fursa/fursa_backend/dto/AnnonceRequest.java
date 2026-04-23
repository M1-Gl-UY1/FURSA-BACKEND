package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AnnonceRequest(
        @NotNull Long vendeurId,
        @NotNull Long proprieteId,
        @NotNull @Min(1) Integer nombreDePartsAVendre,
        @NotNull @Positive BigDecimal prixUnitaireDemande
) {}
