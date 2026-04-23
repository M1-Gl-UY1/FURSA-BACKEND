package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;

public record DashboardAdminResponse(
        long nombreInvestisseurs,
        long nombreProprietes,
        long nombreProprietesPubliees,
        int totalPartsEmises,
        int totalPartsVendues,
        BigDecimal volumeTransactions,
        BigDecimal totalDividendesDistribues,
        long nombreAnnoncesOuvertes
) {}
