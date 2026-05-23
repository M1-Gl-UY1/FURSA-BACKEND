-- Phase 10b : fenetre de declaration des revenus + penalite retard
-- Regle metier : declarations ouvertes du 1er au 5 de chaque mois (pour le mois N-1).
-- Apres le 5 : declaration acceptee mais 300 EUR retenus, geres en compte central FURSA.

ALTER TABLE revenus
    ADD COLUMN IF NOT EXISTS penalite_retard NUMERIC(15, 2) NOT NULL DEFAULT 0;

-- Sanity check : la penalite ne peut pas exceder le montant total declare,
-- et ne peut pas etre negative.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_revenus_penalite_valide'
    ) THEN
        ALTER TABLE revenus
            ADD CONSTRAINT chk_revenus_penalite_valide
            CHECK (penalite_retard >= 0 AND penalite_retard <= montant_total);
    END IF;
END $$;

-- Index sur les declarations en retard, pour la query admin "liste des retards".
CREATE INDEX IF NOT EXISTS idx_revenus_penalite_nonzero
    ON revenus (penalite_retard) WHERE penalite_retard > 0;
