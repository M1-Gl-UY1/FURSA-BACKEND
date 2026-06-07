package com.fursa.fursa_backend.model.enumeration;

/**
 * V2 R (07/06/2026) : etat d'un job de la queue de sync blockchain.
 *
 *   PENDING : en attente d'execution (immediate ou apres backoff)
 *   SUCCESS : tx broadcast OK (txHash present)
 *   FAILED  : 5 retries epuises, abandon (intervention manuelle requise)
 */
public enum StatutSyncBlockchain {
    PENDING,
    SUCCESS,
    FAILED
}
