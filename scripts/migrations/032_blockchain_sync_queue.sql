-- V2 R (07/06/2026) : queue persistante des sync blockchain.
--
-- Toute operation on-chain (BlockchainSyncService.pushPrix, .pushStatut,
-- RevenueLedgerService.enregistrerRevenu, .enregistrerDistribution) qui echoue
-- (RPC down, gas trop bas, nonce invalide) est persistee ici, puis ressayee
-- par un worker scheduled avec backoff exponentiel (5 retries max).
--
-- Idempotente, additive.

CREATE TABLE IF NOT EXISTS blockchain_sync_queue (
    id              BIGSERIAL PRIMARY KEY,
    -- Type d'operation : SYNC_PRIX, SET_STATUT, ENREGISTRER_REVENU, ENREGISTRER_DISTRIBUTION
    type            VARCHAR(40) NOT NULL,
    -- Reference fonctionnelle vers l'entite source (propriete_id, revenu_id...).
    -- Sert au debug + au regroupement, pas a la deduplication.
    ref_id          BIGINT,
    -- Payload JSON contenant tous les parametres de l'appel on-chain.
    -- Permet de re-executer sans recharger les entites BDD (si elles ont change entre temps).
    payload         JSONB NOT NULL,
    -- PENDING (en attente), SUCCESS (tx broadcast OK, txHash present), FAILED (5 retries epuises).
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    -- Date du prochain essai (NULL = a executer immediatement).
    next_attempt_at TIMESTAMP,
    last_attempt_at TIMESTAMP,
    last_error      TEXT,
    -- Tx hash si SUCCESS (audit + lien Etherscan).
    tx_hash         VARCHAR(80),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sync_queue_status
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_sync_queue_type
        CHECK (type IN ('SYNC_PRIX', 'SET_STATUT', 'ENREGISTRER_REVENU', 'ENREGISTRER_DISTRIBUTION'))
);

-- Index pour le worker : trouver rapidement les PENDING prets a executer.
CREATE INDEX IF NOT EXISTS idx_sync_queue_pending
    ON blockchain_sync_queue (status, next_attempt_at)
    WHERE status = 'PENDING';

-- Index pour la console admin : tri par date.
CREATE INDEX IF NOT EXISTS idx_sync_queue_created
    ON blockchain_sync_queue (created_at DESC);

-- Index pour cross-ref BDD (page diagnostic propriete : voir les sync d'un bien).
CREATE INDEX IF NOT EXISTS idx_sync_queue_ref
    ON blockchain_sync_queue (type, ref_id);
