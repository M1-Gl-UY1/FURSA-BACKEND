package com.fursa.fursa_backend.model.enumeration;

/**
 * Phase 10a : type de mouvement sur un wallet utilisateur.
 *
 * Chaque mouvement est immuable (append-only) et trace une operation comptable
 * sur le solde du wallet. Le signe du montant indique credit (+) ou debit (-).
 */
public enum TypeWalletTransaction {
    /** Recharge du wallet via PSP (Mobile Money / Yellow Card / Mock). */
    TOPUP,

    /** Debit lors de l'achat de parts sur le marche primaire (escrow propriete). */
    DEBIT_ACHAT_PARTS,

    /** Debit lors de l'achat de parts sur le marche secondaire (annonce). */
    DEBIT_ACHAT_REVENTE,

    /** Credit recu lors d'une distribution de dividende (apres commission FURSA). */
    CREDIT_DIVIDENDE,

    /** Credit recu par un proprietaire suite a un retrait validation par admin
     *  (fonds debloques depuis l'escrow propriete). */
    CREDIT_VENTE_PARTS,

    /** Credit recu suite a une revente sur le marche secondaire. */
    CREDIT_REVENTE,

    /** Credit de remboursement si une collecte de propriete a echoue / a ete annulee. */
    CREDIT_REFUND_ACHAT,

    /** Debit suite a une demande de retrait validee par admin (sortie vers MM / virement / crypto). */
    DEBIT_WITHDRAW,

    /** Ajustement manuel par un admin (correction d'erreur, support utilisateur). Trace le motif. */
    AJUSTEMENT_ADMIN
}
