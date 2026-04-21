package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.Annonce;
import lombok.Data;

@Data
public class AnnonceDTO {
    private Long id;
    private Long sellerId;
    private String sellerName;
    private Long propertyId;
    private String propertyName;
    private Integer partsCount;
    private Double pricePerPart;
    private Double totalPrice;
    private String status;
    private String createdAt;

    public static AnnonceDTO from(Annonce annonce) {
        AnnonceDTO dto = new AnnonceDTO();
        dto.setId(annonce.getId());
        dto.setSellerId(annonce.getInvestisseur().getId());
        dto.setSellerName(annonce.getInvestisseur().getNom() + " " + annonce.getInvestisseur().getPrenom());
        dto.setPropertyId(annonce.getPropriete().getId());
        dto.setPropertyName(annonce.getPropriete().getNom());
        dto.setPartsCount(annonce.getNombreDePartsAVendre());
        dto.setPricePerPart(annonce.getPrixUnitaireDemande().doubleValue());
        dto.setTotalPrice(annonce.getPrixTotal());
        dto.setStatus(annonce.getStatut().name());
        dto.setCreatedAt(annonce.getDateCreation().toString());
        return dto;
    }
}