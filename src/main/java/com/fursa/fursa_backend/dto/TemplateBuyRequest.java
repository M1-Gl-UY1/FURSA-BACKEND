package com.fursa.fursa_backend.dto;

import lombok.Data;

@Data
public class TemplateBuyRequest {
    private Long propertyId;
    private Long buyerId;
    private Integer partsCount;
    private Double amount;
    private Long sellerId;
}