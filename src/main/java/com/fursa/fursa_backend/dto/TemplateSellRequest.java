package com.fursa.fursa_backend.dto;

import lombok.Data;

@Data
public class TemplateSellRequest {
    private Long propertyId;
    private Long sellerId;
    private Integer partsCount;
    private Double pricePerPart;
}