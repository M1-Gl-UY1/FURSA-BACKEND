package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.RegleObligationDoc;

public record CategorieDocumentResponse(
        Long id,
        String code,
        String label,
        String description,
        String icone,
        Integer ordre,
        Boolean actif,
        RegleObligationDoc regleObligation
) {}
