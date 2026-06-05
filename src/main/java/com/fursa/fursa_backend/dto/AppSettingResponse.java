package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.TypeSetting;

public record AppSettingResponse(
        String cle,
        String valeur,
        TypeSetting type,
        String label,
        String description,
        String groupe,
        String unite,
        Integer ordre
) {}
