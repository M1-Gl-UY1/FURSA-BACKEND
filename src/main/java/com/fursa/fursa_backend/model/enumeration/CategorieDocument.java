package com.fursa.fursa_backend.model.enumeration;

/**
 * P8 (Hugh 22/05/2026) : typage des documents legaux uploades en phase
 * Certification.
 *
 * "on va demander les documents qui vont prouver que effectivement
 *  l'appartement la est rentable tel que soit un contrat de bail, soit
 *  des releves qui viennent de Airbnb si c'est que c'etait exploite sur
 *  Airbnb ou bien d'un autre partenaire" (Hugh, 00:42:06)
 */
public enum CategorieDocument {
    /** Titre foncier officiel. */
    TITRE_FONCIER,

    /** Permis de construire / certificat de conformite. */
    PERMIS_CONSTRUIRE,

    /** Contrat de gestion locative (partenaire TOC, Airbnb, etc.). */
    CONTRAT_GESTION,

    /** Contrat de bail (preuve revenu locatif long terme). */
    CONTRAT_BAIL,

    /** Releve Airbnb (preuve revenu courte duree). */
    RELEVE_AIRBNB,

    /** Tout autre document legal. */
    AUTRE,
}
