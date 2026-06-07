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
        /** Adresse du contrat propriete concernee (REVENU/DIVIDENDE). Null pour KYC. */
        String proprieteToken,
        /** REVENU_ENREGISTRE : trimestre format YYYYQ (ex: 20262 = Q2 2026). */
        Integer trimestre,
        /** REVENU_ENREGISTRE : montant USD entier. DIVIDENDE_DISTRIBUE : idem. */
        BigInteger montantUsd,
        /** REVENU_ENREGISTRE : sha256 0x... du justificatif (preuve d'existence). KYC : hash KYC. */
        String hashJustificatif,
        /** REVENU_ENREGISTRE/KYC : timestamp UNIX. */
        Long dateValidation,
        /** DIVIDENDE_DISTRIBUE : adresse de l'investisseur destinataire. KYC : wallet titulaire. */
        String investisseur,
        /** Id BDD du revenu source. KYC : null. */
        BigInteger revenuIdBackend,
        /** KYC_ENREGISTRE : timestamp UNIX d'expiration. */
        Long kycExpireLe,
        /** KYC_REVOQUE : motif de revocation. */
        String motif
) {
    public enum Type {
        REVENU_ENREGISTRE,
        DIVIDENDE_DISTRIBUE,
        // V2 T (07/06/2026) : KYC on-chain
        KYC_ENREGISTRE,
        KYC_REVOQUE
    }
}
