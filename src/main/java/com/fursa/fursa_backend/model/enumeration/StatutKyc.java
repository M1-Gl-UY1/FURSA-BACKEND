package com.fursa.fursa_backend.model.enumeration;

public enum StatutKyc {
    /** Soumission cree par l'investisseur, en attente de prise en charge admin. */
    PENDING,
    /** Un admin a commence l'examen mais n'a pas encore tranche (etat de travail). */
    IN_REVIEW,
    /** Soumission approuvee. investisseur.isVerified passe a true. */
    APPROVED,
    /** Soumission refusee. Motif de refus stocke. Investisseur peut re-soumettre. */
    REJECTED,
    /** KYC valide mais expire (apres 12-24 mois). Re-submission obligatoire. */
    EXPIRED
}
