package com.fursa.fursa_backend.model.enumeration;

/**
 * P2 (Hugh 22/05/2026) : etats d'une inscription en liste d'attente.
 */
public enum StatutListeAttente {
    /** Dans la file FIFO, attend qu'une part redevienne disponible. */
    EN_ATTENTE,

    /** Notifie qu'une part est dispo. Peut acheter (V1 : non utilise). */
    NOTIFIE,

    /** A achete au moins une part suite a sa notif. */
    SERVI,

    /** Retire de la file par l'investisseur. */
    ANNULE,
}
