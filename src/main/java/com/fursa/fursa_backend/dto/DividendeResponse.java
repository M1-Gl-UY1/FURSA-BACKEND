package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.StatutPaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DividendeResponse(
        Long id,
        Long revenuId,
        Long proprieteId,
        String proprieteNom,
        Long investisseurId,
        BigDecimal montantCalcule,
        LocalDate dateDistribution,
        StatutPaiement statut,
        String hashTransaction
) {}
