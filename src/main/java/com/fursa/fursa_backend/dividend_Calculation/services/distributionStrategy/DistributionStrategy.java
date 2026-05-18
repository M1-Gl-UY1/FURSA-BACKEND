package com.fursa.fursa_backend.dividend_Calculation.services.distributionStrategy;

import java.math.BigDecimal;

public interface DistributionStrategy {
    BigDecimal calculerMontant(
        BigDecimal montantTotal,
        int partsInvestisseur,
        int totalParts
    );
}