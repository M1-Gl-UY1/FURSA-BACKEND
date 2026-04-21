package com.fursa.fursa_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseResult {
    private boolean success;
    private String transactionHash;
    private Double montantTotal;
    private Integer nombreParts;
    private String message;
}