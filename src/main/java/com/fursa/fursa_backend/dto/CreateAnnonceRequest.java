// CreateAnnonceRequest.java - DTO
package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateAnnonceRequest {
    @NotNull
    private Long proprieteId;

    @NotNull
    @Positive
    private Integer nombreParts;

    @NotNull
    @Positive
    private BigDecimal prixUnitairePart;
}