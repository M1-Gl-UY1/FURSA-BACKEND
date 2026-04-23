package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;

public record DashboardInvestisseurResponse(
        Long investisseurId,
        int nombreProprietes,
        int totalParts,
        BigDecimal totalInvesti,
        BigDecimal valeurPortefeuille,
        BigDecimal totalDividendesRecus,
        BigDecimal revenusAnnuelsPrevus,
        int nombreAnnoncesOuvertes,
        int nombreNotificationsNonLues
) {}
