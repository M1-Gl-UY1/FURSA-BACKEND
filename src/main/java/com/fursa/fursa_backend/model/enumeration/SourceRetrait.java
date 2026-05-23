package com.fursa.fursa_backend.model.enumeration;

/**
 * Phase 10e : source des fonds a retirer.
 *
 * - WALLET : retrait depuis le wallet interne FURSA du user (cash dehors).
 *            Concerne investisseurs (dividendes recus) ET proprietaires
 *            (apres deblocage d'escrow vers wallet).
 * - ESCROW_PROPRIETE : retrait par le proprietaire des fonds collectes sur
 *            son bien FINANCE. Va vers le wallet interne du proprio (1ere etape).
 *            Le proprio peut ensuite faire un retrait WALLET vers MM/virement.
 */
public enum SourceRetrait {
    WALLET,
    ESCROW_PROPRIETE
}
