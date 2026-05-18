package com.fursa.fursa_backend.dividend_Calculation.services.distributionStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

@Service
public class ProrataDistributionStrategy implements DistributionStrategy {

    @Override
    public BigDecimal calculerMontant(BigDecimal montantTotal, int partsInvestisseur, int totalParts) {
        if (totalParts == 0) {
            return BigDecimal.ZERO; // Éviter la division par zéro
        }
        return montantTotal
                .multiply(BigDecimal.valueOf(partsInvestisseur))
                .divide(BigDecimal.valueOf(totalParts), 2, RoundingMode.HALF_UP);
    }

}
