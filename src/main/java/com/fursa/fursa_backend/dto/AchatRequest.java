package com.fursa.fursa_backend.dto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AchatRequest {
    @NotNull
    private Long annonceId;

    @NotNull
    @Positive
    private Integer nombreParts;
}