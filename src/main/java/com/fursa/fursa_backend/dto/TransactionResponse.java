package com.fursa.fursa_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class TransactionResponse {
    private Long transactionId;
    private String hashTransaction;
    private String typeOperation;
    private String statut;
    private Integer nombreParts;
    private BigDecimal montant;
    private String proprieteNom;
    private LocalDateTime dateTransaction;
}
