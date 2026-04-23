package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;

public record AchatAnnonceResponse(
        Long annonceId,
        Long transactionId,
        Long paiementId,
        Long acheteurId,
        Long vendeurId,
        Long proprieteId,
        Integer nombreDePartsAchetees,
        BigDecimal montantTotal,
        String hashTransaction,
        String statutAnnonce
) {}
