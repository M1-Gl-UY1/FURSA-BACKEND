package com.fursa.fursa_backend.model.enumeration;

/** Cycle de vie d'un revenu (Phase 8). */
public enum StatutRevenu {
    EN_REVIEW,   // soumis par un propriétaire, en attente de validation admin
    VALIDE,      // validé par admin, prêt à être distribué
    REFUSE,      // refusé par admin avec motif
    DISTRIBUE    // distribution exécutée (dividendes générés)
}
