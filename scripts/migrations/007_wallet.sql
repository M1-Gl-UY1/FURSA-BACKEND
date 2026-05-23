-- Phase 10a : wallet polymorphique (1 par user, investisseur ou proprietaire)
-- - Table wallet : 1 ligne par user, solde EUR avec @Version optimistic lock
-- - Table wallet_transaction : journal append-only des mouvements, signed amount

-- ============================================================================
-- WALLET : 1 par user
-- ============================================================================
CREATE TABLE IF NOT EXISTS wallet (
    id_wallet      BIGSERIAL PRIMARY KEY,
    id_user        BIGINT NOT NULL,
    solde          NUMERIC(15, 2) NOT NULL DEFAULT 0,
    devise         VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_wallet_user UNIQUE (id_user),
    CONSTRAINT fk_wallet_user FOREIGN KEY (id_user) REFERENCES users(id_user) ON DELETE CASCADE,
    CONSTRAINT chk_wallet_solde_positif CHECK (solde >= 0),
    CONSTRAINT chk_wallet_devise CHECK (devise IN ('USD', 'EUR'))
);

CREATE INDEX IF NOT EXISTS idx_wallet_user ON wallet (id_user);

-- ============================================================================
-- WALLET_TRANSACTION : journal append-only
-- ============================================================================
CREATE TABLE IF NOT EXISTS wallet_transaction (
    id_wtx         BIGSERIAL PRIMARY KEY,
    id_wallet      BIGINT NOT NULL,
    type           VARCHAR(32) NOT NULL,
    montant        NUMERIC(15, 2) NOT NULL,
    solde_apres    NUMERIC(15, 2) NOT NULL,
    ref_table      VARCHAR(50),
    ref_id         BIGINT,
    libelle        VARCHAR(255),
    metadata       TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_walletTx_wallet FOREIGN KEY (id_wallet) REFERENCES wallet(id_wallet) ON DELETE RESTRICT,
    CONSTRAINT chk_walletTx_solde_apres CHECK (solde_apres >= 0),
    CONSTRAINT chk_walletTx_type CHECK (type IN (
        'TOPUP', 'DEBIT_ACHAT_PARTS', 'DEBIT_ACHAT_REVENTE',
        'CREDIT_DIVIDENDE', 'CREDIT_VENTE_PARTS', 'CREDIT_REVENTE',
        'CREDIT_REFUND_ACHAT', 'DEBIT_WITHDRAW', 'AJUSTEMENT_ADMIN'
    ))
);

CREATE INDEX IF NOT EXISTS idx_walletTx_wallet ON wallet_transaction (id_wallet, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_walletTx_type ON wallet_transaction (type);
CREATE INDEX IF NOT EXISTS idx_walletTx_ref ON wallet_transaction (ref_table, ref_id);

-- ============================================================================
-- WALLETS RETROACTIFS : creer un wallet vide pour tous les users existants
-- (pour eviter les requetes orphelines apres deploiement)
-- ============================================================================
INSERT INTO wallet (id_user, solde, devise)
SELECT u.id_user, 0, 'USD'
FROM users u
LEFT JOIN wallet w ON w.id_user = u.id_user
WHERE w.id_wallet IS NULL
  AND u.deleted_at IS NULL;

-- ============================================================================
-- Protection append-only : on interdit UPDATE/DELETE sur wallet_transaction
-- en environnement prod (controle applicatif backup). Commenter si besoin
-- de fixes manuels exceptionnels (auquel cas tracer en commentaire SQL).
-- ============================================================================
-- (Volontairement absent en MVP : on garde la souplesse pour les corrections
-- de bug initiales. A activer en V2 quand le code est stabilise.)
