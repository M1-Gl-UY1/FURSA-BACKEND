package com.fursa.fursa_backend.model.enumeration;

/**
 * P1 (reunion Hugh 22/05/2026) : section structuree d'une photo de bien.
 *
 * Au lieu d'un drag/drop generique, le wizard impose au proprietaire de
 * categoriser chaque photo (facade, salon, etc.) pour que la fiche du bien
 * soit lisible et professionnelle cote investisseur.
 *
 * Certaines sections sont conditionnelles (PISCINE seulement si has_piscine).
 */
public enum SectionPhoto {
    FACADE,
    SALON,
    CUISINE,
    CHAMBRE,
    SALLE_DE_BAIN,
    PISCINE,
    EXTERIEUR,
    VUE,
    AUTRE
}
