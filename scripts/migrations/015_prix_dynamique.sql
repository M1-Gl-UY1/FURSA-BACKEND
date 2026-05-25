-- ============================================================================
-- Phase P1 (Hugh 22/05/2026) : prix dynamique des parts
-- ============================================================================
-- "il faut un mecanisme pour que la valeur puisse augmenter... la rentabilite
--  des appartements ne sont pas les memes." (Hugh, 00:36:59)
--
-- Le prix unitaire d'une part fluctue selon :
--   - la rentabilite reelle vs prevue (cumul des declarations trimestrielles)
--   - la demande (liste d'attente / nb total parts)
--
-- Formule : prix_courant = prix_initial * (1 + bonus_rentabilite + bonus_demande)
-- ============================================================================

-- 1) On garde le prix initial sur la propriete pour pouvoir recalculer.
--    Le champ existant prix_unitaire_part devient le PRIX COURANT.
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS prix_initial_part NUMERIC(15, 2);

-- Pour les biens existants, on initialise prix_initial = prix_unitaire courant.
UPDATE propriete
    SET prix_initial_part = prix_unitaire_part
    WHERE prix_initial_part IS NULL;

-- 2) Bonus cumule de rentabilite (somme des contributions trimestrielles).
--    Stocke en fraction : 0.05 = +5%. Sign possible negatif.
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS bonus_rentabilite_total NUMERIC(8, 6) NOT NULL DEFAULT 0;

-- 3) Bonus demande instantane (calcule a partir de la liste d'attente).
--    Stocke en fraction : 0.10 = +10%. Toujours positif ou zero.
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS bonus_demande NUMERIC(8, 6) NOT NULL DEFAULT 0;

-- ============================================================================
-- Historique des changements de prix
-- ============================================================================
CREATE TABLE IF NOT EXISTS historique_prix_part (
    id BIGSERIAL PRIMARY KEY,
    id_prop BIGINT NOT NULL REFERENCES propriete(id_prop) ON DELETE CASCADE,

    -- Snapshot du prix au moment du changement
    prix_unitaire NUMERIC(15, 2) NOT NULL,
    prix_initial NUMERIC(15, 2) NOT NULL,
    bonus_rentabilite_total NUMERIC(8, 6) NOT NULL DEFAULT 0,
    bonus_demande NUMERIC(8, 6) NOT NULL DEFAULT 0,

    -- Pourquoi le prix a change a ce moment-la
    raison VARCHAR(40) NOT NULL,
    -- Valeurs : INITIALE | DECLARATION_REVENU_VALIDEE | CRON_TRIMESTRIEL
    --        | LISTE_ATTENTE_CHANGEE | TRADE_SECONDAIRE | AJUSTEMENT_ADMIN

    -- ID externe lie a la raison (revenu_id, transaction_id, etc.). Nullable.
    source_id BIGINT,

    -- Pourcentage de variation par rapport au prix INITIAL (pour affichage rapide).
    variation_pct NUMERIC(8, 4) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_historique_prix_raison
        CHECK (raison IN (
            'INITIALE',
            'DECLARATION_REVENU_VALIDEE',
            'CRON_TRIMESTRIEL',
            'LISTE_ATTENTE_CHANGEE',
            'TRADE_SECONDAIRE',
            'AJUSTEMENT_ADMIN'
        ))
);

CREATE INDEX IF NOT EXISTS idx_historique_prix_prop
    ON historique_prix_part (id_prop, created_at DESC);

-- Seed : pour chaque propriete existante, creer une entree INITIALE.
INSERT INTO historique_prix_part
    (id_prop, prix_unitaire, prix_initial, bonus_rentabilite_total, bonus_demande, raison, variation_pct, created_at)
SELECT
    p.id_prop,
    p.prix_unitaire_part,
    p.prix_initial_part,
    0, 0,
    'INITIALE',
    0,
    COALESCE(p.date_creation::timestamp, CURRENT_TIMESTAMP)
FROM propriete p
WHERE NOT EXISTS (
    SELECT 1 FROM historique_prix_part h WHERE h.id_prop = p.id_prop
);
