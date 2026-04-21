// PossessionDTO.java - Correction
package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.Possession;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PossessionDTO {
    private Long id;
    private Long propertyId;
    private String propertyName;
    private Integer partsCount;
    private Double purchasePrice;
    private Double currentValue;

    public static PossessionDTO from(Possession possession) {
        PossessionDTO dto = new PossessionDTO();
        dto.setId(possession.getId());
        dto.setPropertyId(possession.getPropriete().getId());
        dto.setPropertyName(possession.getPropriete().getNom());
        dto.setPartsCount(possession.getNombreDeParts());
        dto.setPurchasePrice(possession.getPrixAchat());

        // Correction: Integer * BigDecimal
        BigDecimal current = BigDecimal.valueOf(possession.getNombreDeParts())
                .multiply(possession.getPropriete().getPrixUnitairePart());
        dto.setCurrentValue(current.doubleValue());

        return dto;
    }
}