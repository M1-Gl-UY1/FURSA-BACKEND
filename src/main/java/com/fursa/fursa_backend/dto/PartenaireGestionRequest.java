package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.TypePartenaire;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PartenaireGestionRequest(
        @NotBlank @Size(max = 150) String nom,
        @NotNull TypePartenaire typePartenaire,
        @Size(max = 500) String description,
        @Size(max = 300) String siteWeb,
        @Size(max = 150) String contactEmail,
        @Size(max = 500) String logoUrl,
        Boolean actif
) {}
