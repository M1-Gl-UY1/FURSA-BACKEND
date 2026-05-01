package com.fursa.fursa_backend.model.enumeration;

public enum StatutPropriete {
    // Workflow historique (admin direct)
    EN_ATTENTE,
    PUBLIEE,
    REJETEE,

    // Workflow soumission propriétaire (Phase 7)
    EN_REVIEW,   // soumise par un investisseur, en attente d'examen admin
    ACCEPTEE,    // validée par admin, prête à publier
    REFUSEE      // refusée par admin avec motif
}
