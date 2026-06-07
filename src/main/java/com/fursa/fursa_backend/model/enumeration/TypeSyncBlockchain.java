package com.fursa.fursa_backend.model.enumeration;

/**
 * V2 R (07/06/2026) : type d'operation on-chain a synchroniser.
 *
 * Chaque type correspond a une methode des services Phase O + P :
 *   - SYNC_PRIX              -> BlockchainSyncService.pushPrixCourant
 *   - SET_STATUT             -> BlockchainSyncService.pushStatut
 *   - ENREGISTRER_REVENU     -> RevenueLedgerService.enregistrerRevenu
 *   - ENREGISTRER_DISTRIBUTION -> RevenueLedgerService.enregistrerDistributionBatch
 */
public enum TypeSyncBlockchain {
    SYNC_PRIX,
    SET_STATUT,
    ENREGISTRER_REVENU,
    ENREGISTRER_DISTRIBUTION,
    // V2 T (07/06/2026) : KYC on-chain
    ENREGISTRER_KYC,
    REVOQUER_KYC
}
