package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Recharge du wallet (mode demo / mock).
 *
 * Aucun PSP reel n'est branche : le solde est credite directement. Plafond
 * eleve (1 000 000 USD) en mode demo pour ne pas bloquer les tests. A
 * resserrer drastiquement quand un vrai PSP (Yellow Card / Mobile Money)
 * sera branche.
 */
public record RechargeRequest(
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "1.00", message = "Le montant minimum est 1 USD")
        @DecimalMax(value = "1000000.00", message = "Le montant maximum par recharge est 1 000 000 USD")
        BigDecimal montant,

        /** Methode affichee (mock) : MOBILE_MONEY | VIREMENT | CARTE. Optionnel. */
        String methode
) {}
