package com.fursa.fursa_backend.model.enumeration;

/**
 * Phase 10c : etat des parts detenues par un investisseur sur une propriete.
 *
 * - PENDING : achat effectue, parts reservees, mais collecte pas encore atteint le seuil
 *             de 80%. L'investisseur ne touche PAS de dividendes tant que la propriete
 *             n'est pas FINANCEE.
 * - ACTIVE  : la propriete a atteint le seuil de 80%, les parts sont actives, l'investisseur
 *             percoit les dividendes au prorata.
 * - ANNULEE : la collecte a ete annulee. Les parts sont supprimees, l'investisseur a ete
 *             rembourse integralement sur son wallet.
 */
public enum StatutPossession {
    PENDING,
    ACTIVE,
    ANNULEE
}
