package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.StatutRevenu;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenuResponse(
        Long id,
        Long proprieteId,
        String proprieteNom,
        LocalDate date,
        BigDecimal montantTotal,
        // Phase 8
        Long proposeurId,
        StatutRevenu statut,
        String motifRefus,
        LocalDate periodeDebut,
        LocalDate periodeFin
) {}
