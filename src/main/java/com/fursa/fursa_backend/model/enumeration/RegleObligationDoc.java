package com.fursa.fursa_backend.model.enumeration;

/**
 * V2 G.2 (04/06/2026) : regle d'obligation d'une categorie de document
 * lors de la finalisation d'un brouillon.
 *
 * <p>Stockee dans {@code categorie_document.regle_obligation}. Pour les 6
 * codes historiques, refleter la regle metier actuelle :
 * <ul>
 *   <li>TITRE_FONCIER → TOUJOURS</li>
 *   <li>PERMIS_CONSTRUIRE → SI_NEUF_OU_CONSTRUCTION</li>
 *   <li>CONTRAT_GESTION, CONTRAT_BAIL → SI_DEJA_RENTABLE (groupe : au moins un)</li>
 *   <li>RELEVE_AIRBNB, AUTRE → OPTIONNEL</li>
 * </ul>
 *
 * <p>Les codes custom crees par l'admin sont OPTIONNEL par defaut.
 */
public enum RegleObligationDoc {
    /** Obligatoire pour tous les biens. */
    TOUJOURS,

    /** Obligatoire si statutExploitation = NEUF ou EN_CONSTRUCTION. */
    SI_NEUF_OU_CONSTRUCTION,

    /**
     * Obligatoire si statutExploitation = DEJA_RENTABLE.
     * Si plusieurs categories ont cette regle, au moins UNE doit etre fournie.
     */
    SI_DEJA_RENTABLE,

    /** Jamais obligatoire (catalogue de proposition uniquement). */
    OPTIONNEL,
}
