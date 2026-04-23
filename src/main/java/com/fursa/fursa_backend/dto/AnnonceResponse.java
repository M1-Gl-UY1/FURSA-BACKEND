package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;

import java.math.BigDecimal;

public record AnnonceResponse(
        Long id,
        Long vendeurId,
        String vendeurNom,
        Long proprieteId,
        String proprieteNom,
        Integer nombreDePartsAVendre,
        BigDecimal prixUnitaireDemande,
        StatutAnnonce statut
) {}
