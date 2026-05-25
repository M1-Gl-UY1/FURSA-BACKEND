package com.fursa.fursa_backend.model.enumeration;

/**
 * Raison pour laquelle le prix unitaire d'une part a ete recalcule.
 *
 * P1 (Hugh 22/05/2026) - "il faut un mecanisme pour que la valeur puisse augmenter
 * et puis quand un appartement est rentable, ca fait augmenter sa valeur..."
 */
public enum RaisonRecalculPrix {
    /** Snapshot initial a la creation du bien. */
    INITIALE,
    /** Un revenu trimestriel a ete valide par l'admin -> recalcul du bonus rentabilite. */
    DECLARATION_REVENU_VALIDEE,
    /** Cron trimestriel apres fermeture de la fenetre de declaration. */
    CRON_TRIMESTRIEL,
    /** Inscription/desinscription liste d'attente (chantier P2). */
    LISTE_ATTENTE_CHANGEE,
    /** Volume significatif de reventes au-dessus du prix FURSA (chantier P3). */
    TRADE_SECONDAIRE,
    /** Ajustement manuel par un admin. */
    AJUSTEMENT_ADMIN,
}
