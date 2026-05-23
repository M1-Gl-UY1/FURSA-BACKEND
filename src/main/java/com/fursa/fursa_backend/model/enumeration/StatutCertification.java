package com.fursa.fursa_backend.model.enumeration;

/**
 * Phase Certification (Hugh 22/05/2026) : etat de certification d'un bien.
 *
 * - NON_CERTIFIE : proprio n'a pas encore soumis ses documents legaux.
 *                  Le bien est visible/publie mais NON ACHETABLE.
 * - EN_REVIEW    : proprio a uploade les documents + soumis pour certif.
 *                  Admin doit valider.
 * - CERTIFIE     : admin a verifie tous les documents legaux, bien tag "Certifie".
 *                  ACHAT DEBLOQUE.
 * - REFUSEE      : admin a refuse la certification (documents incomplets / faux).
 *                  Le proprio peut re-uploader et resoumettre.
 *
 * Cette mecanique est SEPAREE du workflow de publication (EN_REVIEW -> ACCEPTEE
 * -> PUBLIEE). Hugh : "validation prealable + certification" = 2 niveaux.
 */
public enum StatutCertification {
    NON_CERTIFIE,
    EN_REVIEW,
    CERTIFIE,
    REFUSEE
}
