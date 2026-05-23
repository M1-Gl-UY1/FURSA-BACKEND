package com.fursa.fursa_backend.model.enumeration;

/**
 * Phase 10c : etat de l'escrow de collecte d'une propriete.
 *
 * - EN_COLLECTE : la collecte est ouverte, les investisseurs peuvent acheter des parts.
 *   L'argent dort sur le compte FURSA. Les Possessions sont PENDING.
 * - FINANCEE   : le seuil de 80% des parts vendues a ete atteint. Les Possessions
 *   sont activees. Le proprietaire peut demander des retraits. La collecte reste
 *   ouverte (les 20% restants peuvent etre achetes).
 * - ANNULEE    : la collecte a ete annulee (par l'admin, decision manuelle).
 *   Les Possessions sont annulees et les investisseurs sont rembourses.
 */
public enum StatutEscrow {
    EN_COLLECTE,
    FINANCEE,
    ANNULEE
}
