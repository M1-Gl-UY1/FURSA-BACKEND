package com.fursa.fursa_backend.model.enumeration;

public enum StatutPaiement {
    EN_ATTENTE,
    VALIDE,
    ANNULE,
    PAYE,      //transaction blockchain confirmée
    ECHOUE
}
