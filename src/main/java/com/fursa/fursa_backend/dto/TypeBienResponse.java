package com.fursa.fursa_backend.dto;

public record TypeBienResponse(
        Long id,
        String code,
        String label,
        String icone,
        Integer ordre,
        Boolean actif,
        Boolean exigeChambres
) {}
