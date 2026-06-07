package com.fursa.fursa_backend.dto;

import java.math.BigInteger;

/**
 * V2 Q (07/06/2026) : event on-chain decode depuis les logs du RevenueLedger.
 *
 * Type d'event :
 *   - REVENU_ENREGISTRE   : un revenu trimestriel ancre on-chain
 *   - DIVIDENDE_DISTRIBUE : un dividende paye a un investisseur
 *
 * Les champs non applicables a un type donne sont null.
 */
public record LedgerEventResponse(
        Type type,
        String txHash,
        BigInteger blockNumber,
        /** Adresse du contrat propriete concernee. */
        String proprieteToken,
        /** REVENU_ENREGISTRE : trimestre format YYYYQ (ex: 20262 = Q2 2026). */
        Integer trimestre,
        /** REVENU_ENREGISTRE : montant USD entier. DIVIDENDE_DISTRIBUE : idem. */
        BigInteger montantUsd,
        /** REVENU_ENREGISTRE : sha256 0x... du justificatif (preuve d'existence). */
        String hashJustificatif,
        /** REVENU_ENREGISTRE : timestamp UNIX de validation. */
        Long dateValidation,
        /** DIVIDENDE_DISTRIBUE : adresse de l'investisseur destinataire. */
        String investisseur,
        /** Id BDD du revenu source (pour cross-ref avec la table revenus). */
        BigInteger revenuIdBackend
) {
    public enum Type {
        REVENU_ENREGISTRE,
        DIVIDENDE_DISTRIBUE
    }
}
