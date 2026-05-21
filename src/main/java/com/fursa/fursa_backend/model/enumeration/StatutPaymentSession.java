package com.fursa.fursa_backend.model.enumeration;

public enum StatutPaymentSession {
    /** Session creee, paiement en cours cote PSP, en attente du webhook. */
    PENDING,
    /** Webhook recu, paiement valide, Possession creee, parts mintees on-chain. */
    CONFIRMED,
    /** expires_at depasse, paiement abandonne. */
    EXPIRED,
    /** PSP a renvoye un echec OU on-chain a echoue apres paiement (necessite intervention admin). */
    FAILED
}
