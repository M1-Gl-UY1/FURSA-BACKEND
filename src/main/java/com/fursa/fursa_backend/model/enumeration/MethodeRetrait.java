package com.fursa.fursa_backend.model.enumeration;

/**
 * Phase 10e : methode de retrait choisie par l'utilisateur.
 *
 * - MOBILE_MONEY : versement via operateur (Orange Money, MTN, Wave, M-Pesa...).
 *                  Reference = numero de telephone.
 * - VIREMENT : virement bancaire SEPA / international. Reference = IBAN.
 * - CRYPTO : transfert vers wallet blockchain externe. Reference = adresse 0x...
 *
 * Pour les retraits depuis l'escrow propriete (proprio -> wallet proprio),
 * la methode n'est pas pertinente : on credite juste le wallet interne.
 * Dans ce cas, on stocke MOBILE_MONEY par defaut (ignore par le flux).
 */
public enum MethodeRetrait {
    MOBILE_MONEY,
    VIREMENT,
    CRYPTO,
    WALLET_INTERNE
}
