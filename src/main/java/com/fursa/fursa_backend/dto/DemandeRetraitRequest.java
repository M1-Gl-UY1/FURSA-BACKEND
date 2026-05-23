package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.MethodeRetrait;
import com.fursa.fursa_backend.model.enumeration.SourceRetrait;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Phase 10e : payload pour creer une demande de retrait (cote user).
 */
public record DemandeRetraitRequest(
        @NotNull SourceRetrait source,
        /** wallet_id si WALLET (calcule cote serveur depuis le user authentifie), propriete_id si ESCROW. */
        Long sourceId,
        @NotNull @DecimalMin(value = "1.00", message = "Montant minimum 1 USD") BigDecimal montant,
        @NotNull MethodeRetrait methode,
        @Size(max = 200) String referenceCible
) {}
