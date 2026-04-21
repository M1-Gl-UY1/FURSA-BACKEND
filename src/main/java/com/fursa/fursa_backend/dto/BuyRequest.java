package com.fursa.fursa_backend.dto;

import lombok.Data;

@Data
public class BuyRequest {
    private Long acheteurId;
    private Integer nombreParts;
}