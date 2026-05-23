package com.fursa.fursa_backend.model.enumeration;

/**
 * P1 (reunion Hugh 22/05/2026) : type de bien immobilier propose sur FURSA.
 *
 * Conditionnne les champs additionnels demandes au proprietaire dans le wizard
 * de creation : nb chambres, equipements (piscine, climatisation, etc.).
 */
public enum TypeBien {
    VILLA,
    APPARTEMENT,
    STUDIO,
    PENTHOUSE,
    DUPLEX,
    IMMEUBLE,
    CHAMBRE
}
