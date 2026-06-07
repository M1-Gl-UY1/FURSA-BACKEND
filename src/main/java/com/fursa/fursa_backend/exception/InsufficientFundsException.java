package com.fursa.fursa_backend.exception;

import java.math.BigDecimal;

/**
 * Phase 10a : levee quand un debit wallet excede le solde disponible.
 *
 * Herite de IllegalStateException pour etre mappee en HTTP 400 par
 * GlobalExceptionHandler sans configuration supplementaire.
 */
public class InsufficientFundsException extends IllegalStateException {

    private final BigDecimal soldeDisponible;
    private final BigDecimal montantDemande;

    public InsufficientFundsException(BigDecimal soldeDisponible, BigDecimal montantDemande) {
        super("Solde insuffisant : " + soldeDisponible + " USD disponibles, "
                + montantDemande + " USD demandes.");
        this.soldeDisponible = soldeDisponible;
        this.montantDemande = montantDemande;
    }

    public BigDecimal getSoldeDisponible() {
        return soldeDisponible;
    }

    public BigDecimal getMontantDemande() {
        return montantDemande;
    }
}
