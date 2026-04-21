package com.fursa.fursa_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO {
    private String message;
    private String transactionHash;
    private Double amount;
    private Boolean success;

    public ResponseDTO(String message, Boolean success) {
        this.message = message;
        this.success = success;
    }

    public ResponseDTO(String message, String transactionHash) {
        this.message = message;
        this.transactionHash = transactionHash;
        this.success = true;
    }
}