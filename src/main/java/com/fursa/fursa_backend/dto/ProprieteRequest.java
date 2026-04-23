package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProprieteRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "La localisation est obligatoire")
    private String localisation;

    private String description;

    @NotNull @Min(1)
    private Integer nombreTotalPart;

    @NotNull @DecimalMin("0.01")
    private BigDecimal prixUnitairePart;

    @NotNull
    private StatutPropriete statut;

    private Double rentabilitePrevue;
}