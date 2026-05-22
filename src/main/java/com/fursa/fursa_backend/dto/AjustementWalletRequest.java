package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Phase 10a : payload pour un ajustement manuel admin sur un wallet.
 *
 * Le montant peut etre positif (credit) ou negatif (debit). Le motif est
 * obligatoire et trace dans WalletTransaction.libelle pour l'audit.
 */
public record AjustementWalletRequest(
        @NotNull BigDecimal montant,
        @NotNull @Size(min = 5, max = 500) String motif
) {}
