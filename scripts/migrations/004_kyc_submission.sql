-- Migration idempotente : table kyc_submission
-- Workflow KYC manuel (Phase 1) : investisseur soumet docs + admin approve/reject.
-- Voir DESIGN_KYC.md pour le flow complet.

CREATE TABLE IF NOT EXISTS kyc_submission (
    id BIGSERIAL PRIMARY KEY,
    id_inv BIGINT NOT NULL REFERENCES users(id_user),
    statut VARCHAR(20) NOT NULL,

    -- Identite
    nationalite VARCHAR(100),
    date_naissance DATE,
    pays_residence VARCHAR(100),
    adresse TEXT,

    -- Documents (url servies par FileController, stockes dans uploads/)
    document_identite_url VARCHAR(500),
    document_domicile_url VARCHAR(500),
    selfie_url VARCHAR(500),

    -- AML
    source_fonds VARCHAR(50),
    is_pep BOOLEAN,
    declaration_sur_honneur BOOLEAN,

    -- Audit
    submitted_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    reviewed_by_admin_id BIGINT,
    motif_refus TEXT,
    nombre_re_submissions INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_kyc_id_inv ON kyc_submission(id_inv);
CREATE INDEX IF NOT EXISTS idx_kyc_statut ON kyc_submission(statut);
CREATE INDEX IF NOT EXISTS idx_kyc_submitted_at ON kyc_submission(submitted_at);
