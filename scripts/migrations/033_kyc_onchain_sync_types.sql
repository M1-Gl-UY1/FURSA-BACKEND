-- V2 T (07/06/2026) : etendre les types acceptes par blockchain_sync_queue
-- pour inclure les operations KYC on-chain (ENREGISTRER_KYC, REVOQUER_KYC).
--
-- Migration idempotente : on drop la contrainte si elle existe puis on la recree.
-- Sans cette extension, le worker leverait une violation de check quand un push
-- KYC echoue et tente l'enqueue.

ALTER TABLE blockchain_sync_queue
    DROP CONSTRAINT IF EXISTS chk_sync_queue_type;

ALTER TABLE blockchain_sync_queue
    ADD CONSTRAINT chk_sync_queue_type
    CHECK (type IN (
        'SYNC_PRIX',
        'SET_STATUT',
        'ENREGISTRER_REVENU',
        'ENREGISTRER_DISTRIBUTION',
        'ENREGISTRER_KYC',
        'REVOQUER_KYC'
    ));
