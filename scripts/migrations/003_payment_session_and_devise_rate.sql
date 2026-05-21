-- Migration idempotente : tables payment_session + devise_rate
-- Session 1 du chantier paiements (cf DESIGN_PAIEMENTS.md sections 3.1, 3.2, 3.3)

CREATE TABLE IF NOT EXISTS devise_rate (
    code_devise VARCHAR(3) PRIMARY KEY,
    taux_vers_usdc NUMERIC(38, 18) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Seed initial des taux de reference (a ajuster par admin via futur endpoint /api/admin/devise-rate/{code})
INSERT INTO devise_rate (code_devise, taux_vers_usdc, updated_at) VALUES
    ('USD', 1.000000000000000000, CURRENT_TIMESTAMP),
    ('EUR', 1.100000000000000000, CURRENT_TIMESTAMP),
    ('XAF', 0.001700000000000000, CURRENT_TIMESTAMP),
    ('XOF', 0.001700000000000000, CURRENT_TIMESTAMP),
    ('KES', 0.007800000000000000, CURRENT_TIMESTAMP),
    ('NGN', 0.000660000000000000, CURRENT_TIMESTAMP),
    ('ZAR', 0.054000000000000000, CURRENT_TIMESTAMP),
    ('GHS', 0.067000000000000000, CURRENT_TIMESTAMP)
ON CONFLICT (code_devise) DO NOTHING;

CREATE TABLE IF NOT EXISTS payment_session (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL UNIQUE,
    id_inv BIGINT NOT NULL REFERENCES users(id_user),
    id_prop BIGINT NOT NULL REFERENCES propriete(id_prop),
    nombre_parts INTEGER NOT NULL CHECK (nombre_parts > 0),
    montant_fiat NUMERIC(19, 2) NOT NULL,
    devise_fiat VARCHAR(3) NOT NULL,
    montant_usdc NUMERIC(38, 18) NOT NULL,
    provider_name VARCHAR(50) NOT NULL,
    widget_url TEXT,
    statut VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    idempotency_key VARCHAR(100),
    paiement_id BIGINT,
    transaction_id BIGINT,
    possession_id BIGINT,
    webhook_raw_payload TEXT,
    error_message VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_payment_session_expires_at ON payment_session(expires_at);
CREATE INDEX IF NOT EXISTS idx_payment_session_statut_provider ON payment_session(statut, provider_name);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_session_idem_inv
    ON payment_session(idempotency_key, id_inv)
    WHERE idempotency_key IS NOT NULL;
