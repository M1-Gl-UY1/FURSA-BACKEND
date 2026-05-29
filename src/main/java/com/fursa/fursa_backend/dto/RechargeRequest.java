package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Recharge du wallet (mode demo / mock).
 *
 * Aucun PSP reel n'est branche : le solde est credite directement. Plafond par
 * recharge pour eviter les abus en attendant l'integration d'un vrai fournisseur
 * de paiement (Yellow Card / Mobile Money).
 */
public record RechargeRequest(
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "1.00", message = "Le montant minimum est 1")
        @DecimalMax(value = "10000.00", message = "Le montant maximum par recharge est 10 000")
        BigDecimal montant,

        /** Methode affichee (mock) : MOBILE_MONEY | VIREMENT | CARTE. Optionnel. */
        String methode
) {}
