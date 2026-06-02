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
    EN_TOKENISATION,

    // Wizard auto-save (Phase 9, 02/06/2026) :
    // soumission incrementale ou chaque etape PATCH des champs. Le bien reste en BROUILLON
    // tant que le proprietaire n'a pas cliqué "Soumettre" a la derniere etape, ce qui
    // bascule en EN_REVIEW. Permet la reprise sur un autre appareil et evite la perte
    // de donnees si l'upload echoue en cours. Cf ProprieteBrouillonService.
    BROUILLON
}
