package com.fursa.fursa_backend.model.enumeration;

public enum StatutPropriete {
    // Workflow historique (admin direct)
    EN_ATTENTE,
    PUBLIEE,
    REJETEE,

    // Workflow soumission propriétaire (Phase 7)
    EN_REVIEW,            // soumise par un investisseur, en attente d'examen admin
    ACCEPTEE,             // validée par admin, prête à publier
    REFUSEE,              // refusée par admin avec motif

    // Workflow tokenisation async (Phase 8, 02/06/2026) :
    // entre ACCEPTEE et PUBLIEE, la tx blockchain est broadcast mais pas encore minee.
    // Un worker scheduled poll le receipt et bascule en PUBLIEE quand l'adresse contrat
    // est disponible. Cf TokenisationWorker.
    EN_TOKENISATION
}
