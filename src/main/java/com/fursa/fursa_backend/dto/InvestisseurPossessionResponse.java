package com.fursa.fursa_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvestisseurPossessionResponse {
    private Long investisseurId;
    private String email;
    private String nom;
    private String prenom;
    private Integer nombreParts;
    private Double pourcentageParts;
}
