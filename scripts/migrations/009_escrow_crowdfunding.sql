-- Phase 10c : crowdfunding par propriete (escrow + Possession statefull)
-- Modele : a l'achat, l'argent va sur EscrowPropriete (pas wallet proprio).
-- Possession en PENDING tant que la collecte n'a pas atteint 80% (Phase 10c bis).

-- ============================================================================
-- Possession : ajouter statut (PENDING / ACTIVE / ANNULEE)
-- ============================================================================
ALTER TABLE possession
    ADD COLUMN IF NOT EXISTS statut VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_possession_statut'
    ) THEN
        ALTER TABLE possession
            ADD CONSTRAINT chk_possession_statut
            CHECK (statut IN ('PENDING', 'ACTIVE', 'ANNULEE'));
    END IF;
END $$;

-- Les possessions historiques (avant Phase 10c) sont supposees acquises -> ACTIVE.
-- C'est deja la valeur par defaut, juste pour etre explicite :
UPDATE possession SET statut = 'ACTIVE' WHERE statut IS NULL OR statut = '';

-- ============================================================================
-- EscrowPropriete : 1 par propriete
-- ============================================================================
CREATE TABLE IF NOT EXISTS escrow_propriete (
    id_escrow         BIGSERIAL PRIMARY KEY,
    id_prop           BIGINT NOT NULL,
    solde             NUMERIC(15, 2) NOT NULL DEFAULT 0,
    total_collecte    NUMERIC(15, 2) NOT NULL DEFAULT 0,
    statut            VARCHAR(16) NOT NULL DEFAULT 'EN_COLLECTE',
    seuil_pct         INTEGER NOT NULL DEFAULT 100,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    financee_le       TIMESTAMP,
    annulee_le        TIMESTAMP,
    motif_annulation  VARCHAR(500),
    version           BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_escrow_propriete UNIQUE (id_prop),
    CONSTRAINT fk_escrow_propriete FOREIGN KEY (id_prop) REFERENCES propriete(id_prop) ON DELETE CASCADE,
    CONSTRAINT chk_escrow_solde_positif CHECK (solde >= 0),
    CONSTRAINT chk_escrow_total_positif CHECK (total_collecte >= 0),
    CONSTRAINT chk_escrow_seuil CHECK (seuil_pct BETWEEN 1 AND 100),
    CONSTRAINT chk_escrow_statut CHECK (statut IN ('EN_COLLECTE', 'FINANCEE', 'ANNULEE'))
);

CREATE INDEX IF NOT EXISTS idx_escrow_propriete ON escrow_propriete (id_prop);
CREATE INDEX IF NOT EXISTS idx_escrow_statut ON escrow_propriete (statut);

-- ============================================================================
-- EscrowTransaction : journal append-only
-- ============================================================================
CREATE TABLE IF NOT EXISTS escrow_transaction (
    id_etx        BIGSERIAL PRIMARY KEY,
    id_escrow     BIGINT NOT NULL,
    type          VARCHAR(32) NOT NULL,
    montant       NUMERIC(15, 2) NOT NULL,
    solde_apres   NUMERIC(15, 2) NOT NULL,
    id_inv        BIGINT,
    ref_table     VARCHAR(50),
    ref_id        BIGINT,
    libelle       VARCHAR(255),
    metadata      TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_escrowTx_escrow FOREIGN KEY (id_escrow) REFERENCES escrow_propriete(id_escrow) ON DELETE RESTRICT,
    CONSTRAINT chk_escrowTx_type CHECK (type IN (
        'CREDIT_ACHAT', 'DEBIT_RETRAIT_PROPRIO', 'DEBIT_COMMISSION_FURSA',
        'DEBIT_REFUND_INVESTISSEUR', 'AJUSTEMENT_ADMIN'
    ))
);

CREATE INDEX IF NOT EXISTS idx_escrowTx_escrow ON escrow_transaction (id_escrow, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_escrowTx_type ON escrow_transaction (type);
CREATE INDEX IF NOT EXISTS idx_escrowTx_inv ON escrow_transaction (id_inv);

-- ============================================================================
-- Escrows retroactifs : pour chaque propriete PUBLIEE / FINANCEE existante,
-- creer un EscrowPropriete avec le total deja collecte calcule depuis Paiement
-- statut PAYE (approximation correcte pour les achats deja effectues).
-- ============================================================================
INSERT INTO escrow_propriete (id_prop, solde, total_collecte, statut)
SELECT
    p.id_prop,
    COALESCE(SUM(pa.montant), 0) AS solde,
    COALESCE(SUM(pa.montant), 0) AS total_collecte,
    CASE
        WHEN p.nombre_total_part > 0
            AND (p.nombre_total_part - COALESCE(p.parts_disponibles, p.nombre_total_part)) * 100.0
                / p.nombre_total_part >= 80
        THEN 'FINANCEE'
        ELSE 'EN_COLLECTE'
    END AS statut
FROM propriete p
LEFT JOIN paiement pa ON pa.id_prop = p.id_prop AND pa.statut = 'VALIDE'
LEFT JOIN escrow_propriete e ON e.id_prop = p.id_prop
WHERE e.id_escrow IS NULL
GROUP BY p.id_prop, p.nombre_total_part, p.parts_disponibles;
