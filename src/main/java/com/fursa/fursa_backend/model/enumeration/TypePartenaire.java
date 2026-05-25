package com.fursa.fursa_backend.model.enumeration;

/**
 * P9 (Hugh 22/05/2026) : types de partenaires FURSA.
 */
public enum TypePartenaire {
    /** Gere la location et l'entretien du bien (Airbnb, CPS Zanzibar, etc.). */
    GESTION_LOCATIVE,

    /** Promoteur immobilier qui vend ses biens via FURSA (Paje Square, Fumba Town). */
    PROMOTEUR_VENTE,

    /** Operations blockchain et infrastructure paiement (SEED Innov). */
    BLOCKCHAIN_OPS,

    /** Expertise locale immobiliere (Africa Bahari). */
    EXPERTISE_LOCALE,
}
