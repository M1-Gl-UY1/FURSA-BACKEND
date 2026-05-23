-- Phase 10e : demandes de retrait (wallet investisseur OU escrow proprio)
-- Modele Hugh 22/05/2026 :
-- - Le user fait une demande, admin valide
-- - 5% commission FURSA prelevee a la validation
-- - Pour les retraits cash externes (MM/virement/crypto), admin execute hors-systeme

CREATE TABLE IF NOT EXISTS demande_retrait (
    id_retrait              BIGSERIAL PRIMARY KEY,
    id_user                 BIGINT NOT NULL,
    source                  VARCHAR(24) NOT NULL,
    id_source               BIGINT NOT NULL,
    montant_demande         NUMERIC(15, 2) NOT NULL,
    commission_fursa        NUMERIC(15, 2) DEFAULT 0,
    montant_final           NUMERIC(15, 2),
    methode                 VARCHAR(24) NOT NULL,
    reference_cible         VARCHAR(200),
    statut                  VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    motif_refus             VARCHAR(500),
    preuve_paiement         VARCHAR(500),
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validee_le              TIMESTAMP,
    completee_le            TIMESTAMP,
    validee_par_admin_id    BIGINT,
    version                 BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_retrait_user FOREIGN KEY (id_user) REFERENCES users(id_user) ON DELETE RESTRICT,
    CONSTRAINT chk_retrait_montant CHECK (montant_demande > 0),
    CONSTRAINT chk_retrait_commission CHECK (commission_fursa >= 0),
    CONSTRAINT chk_retrait_source CHECK (source IN ('WALLET', 'ESCROW_PROPRIETE')),
    CONSTRAINT chk_retrait_methode CHECK (methode IN ('MOBILE_MONEY', 'VIREMENT', 'CRYPTO', 'WALLET_INTERNE')),
    CONSTRAINT chk_retrait_statut CHECK (statut IN ('PENDING', 'APPROVED', 'COMPLETED', 'REFUSED'))
);

CREATE INDEX IF NOT EXISTS idx_retrait_user ON demande_retrait (id_user, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_retrait_statut ON demande_retrait (statut);
CREATE INDEX IF NOT EXISTS idx_retrait_source ON demande_retrait (source, id_source);
