package com.fursa.fursa_backend.model.enumeration;

/**
 * Phase 10e : etat d'une demande de retrait.
 *
 * - PENDING : demande creee par le user, en attente de validation admin.
 *             Le montant est deja deduit du wallet/escrow source (reserve).
 * - APPROVED : admin a valide. Net (apres commission FURSA) credite sur le wallet
 *              destination (cas escrow -> wallet proprio) OU debit confirme du
 *              wallet (cas wallet -> MM/virement, en attente d'execution cash).
 * - COMPLETED : pour les retraits cash externes (MM/virement/crypto), admin a
 *               execute le paiement reel hors-systeme et marque comme termine.
 * - REFUSED : admin a refuse. Le montant reserve est recredite a la source.
 */
public enum StatutDemandeRetrait {
    PENDING,
    APPROVED,
    COMPLETED,
    REFUSED
}
