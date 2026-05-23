package com.fursa.fursa_backend.model.enumeration;

/**
 * P1 (reunion Hugh 22/05/2026) : origine des revenus declares pour un bien
 * "deja rentable" au moment de sa mise en vente sur FURSA.
 *
 * Sert a typer les preuves attendues du proprietaire :
 * - BAIL : contrat de bail long terme a uploader
 * - AIRBNB : releves Airbnb a uploader
 * - AUTRE : tout autre mode d'exploitation (Booking, gestionnaire local, etc.)
 */
public enum SourceRevenu {
    BAIL,
    AIRBNB,
    AUTRE
}
