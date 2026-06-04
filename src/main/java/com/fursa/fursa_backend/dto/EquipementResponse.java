package com.fursa.fursa_backend.dto;

public record EquipementResponse(
        Long id,
        String code,
        String label,
        String icone,
        Integer ordre,
        Boolean actif
) {}
