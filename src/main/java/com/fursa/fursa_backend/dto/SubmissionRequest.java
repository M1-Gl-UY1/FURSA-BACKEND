package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Soumission d'un bien immobilier par un investisseur (Phase 7).
 * Le statut est forcé à EN_REVIEW côté serveur.
 * partsDisponibles = nombreTotalPart à la création.
 */
@Getter
@Setter
public class SubmissionRequest {

    @NotBlank(message = "Le nom du bien est obligatoire")
    private String nom;

    @NotBlank(message = "La localisation est obligatoire")
    private String localisation;

    private String description;

    @NotNull
    @Min(value = 1, message = "Le nombre de parts doit être au moins 1")
    private Integer nombreTotalPart;

    @NotNull
    @DecimalMin(value = "0.01", message = "Le prix unitaire doit être supérieur à 0")
    private BigDecimal prixUnitairePart;

    @NotNull
    private Double rentabilitePrevue;
}
