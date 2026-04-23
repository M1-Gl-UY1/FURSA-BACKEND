package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AchatAnnonceRequest(
        @NotNull Long acheteurId,
        @NotNull @Min(1) Integer nombreDeParts
) {}
