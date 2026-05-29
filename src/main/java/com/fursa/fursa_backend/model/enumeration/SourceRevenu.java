package com.fursa.fursa_backend.model.enumeration;

/**
 * P1 (reunion Hugh 22/05/2026) : operateur / origine des revenus declares pour
 * un bien "deja rentable" au moment de sa mise en vente sur FURSA.
 *
 * FURSA tokenise principalement les biens de ses partenaires promoteurs :
 * - PAJE_SQUARE : bien exploite via Paje Square (gestion TOC).
 * - FUMBA_TOWN : bien exploite via Fumba Town (CPS Africa).
 * - AUTRE : tout autre mode d'exploitation / gestionnaire.
 */
public enum SourceRevenu {
    PAJE_SQUARE,
    FUMBA_TOWN,
    AUTRE
}
