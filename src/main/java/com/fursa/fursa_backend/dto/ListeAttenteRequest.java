package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** P2 (Hugh 22/05/2026) : inscription en liste d'attente. */
public record ListeAttenteRequest(
        @NotNull(message = "proprieteId est obligatoire")
        Long proprieteId,

        @NotNull(message = "nombreParts est obligatoire")
        @Min(value = 1, message = "nombreParts doit etre >= 1")
        @Max(value = 100, message = "nombreParts ne peut pas exceder 100")
        Integer nombreParts
) {}
