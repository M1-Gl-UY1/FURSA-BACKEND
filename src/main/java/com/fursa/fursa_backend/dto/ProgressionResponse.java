package com.fursa.fursa_backend.dto;

public record ProgressionResponse(
        Long proprieteId,
        int totalParts,
        int partsVendues,
        int partsDisponibles,
        double pourcentage
) {}
