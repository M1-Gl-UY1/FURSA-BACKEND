package com.fursa.fursa_backend.model.enumeration;

/**
 * Phase 10c : type de mouvement sur un EscrowPropriete (journal append-only).
 *
 * Le signe du montant indique credit (+) ou debit (-) du solde escrow.
 */
public enum TypeEscrowTransaction {
    /** Investisseur a achete des parts -> argent debite de son wallet, credite ici. */
    CREDIT_ACHAT,

    /** Proprietaire a demande et obtenu un retrait valide par l'admin (Phase 10e). */
    DEBIT_RETRAIT_PROPRIO,

    /** Commission FURSA (5%) prelevee lors d'un retrait proprio. Va au compte central. */
    DEBIT_COMMISSION_FURSA,

    /** Remboursement integral d'un investisseur si la collecte est annulee. */
    DEBIT_REFUND_INVESTISSEUR,

    /** Ajustement manuel par un admin (correction d'erreur, support). */
    AJUSTEMENT_ADMIN
}
