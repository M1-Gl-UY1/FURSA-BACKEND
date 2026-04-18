package com.fursa.fursa_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class AchatResponse {
    private Long paiementId;
    private Long transactionId;
    private String hashTransaction;
    private String statut;
    private Integer nombreParts;
    private BigDecimal montantTotal;
    private String proprieteNom;
    private LocalDateTime dateTransaction;
}
