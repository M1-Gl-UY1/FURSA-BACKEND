package com.fursa.fursa_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class PaiementResponse {
    private Long paiementId;
    private BigDecimal montant;
    private String typePaiement;
    private String statut;
    private Integer nombreParts;
    private String proprieteNom;
    private LocalDateTime date;
}
