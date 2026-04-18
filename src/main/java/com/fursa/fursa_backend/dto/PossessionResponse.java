package com.fursa.fursa_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class PossessionResponse {
    private Long possessionId;
    private String proprieteNom;
    private String proprieteLocalisation;
    private Integer nombreParts;
    private BigDecimal prixUnitairePart;
    private BigDecimal valeurTotale;
    private Double rentabilitePrevue;
}
