package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.TypePartenaire;

public record PartenaireGestionResponse(
        Long id,
        String nom,
        TypePartenaire typePartenaire,
        String description,
        String siteWeb,
        String contactEmail,
        String logoUrl,
        Boolean actif
) {}
