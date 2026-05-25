-- ============================================================================
-- Phase P8b (Hugh 25/05/2026) : etat EN_CONSTRUCTION + date de livraison prevue
-- ============================================================================
-- "les proprietes enregistrer on doit marquer si c'est encore en construction
--  ou si c'est deja fonctionnelle... si une propriete est encore en construction
--  on doit gerer le fait que ca ne genere pas encore de revenus"
--
-- Concretement : tous les biens Paje Square au lancement seront EN_CONSTRUCTION
-- (livraison Q4 2028). Aucune declaration de revenu ne doit etre acceptee
-- tant que le bien n'a pas ete livre (proprio passe en NEUF apres reception).
-- ============================================================================

-- 1) Etendre la contrainte d'enum sur statut_exploitation.
-- En fonction du moteur Postgres, le check existant peut etre nomme; on le
-- recree de facon idempotente.
DO $$
BEGIN
    -- Tenter de supprimer l'ancienne contrainte si elle existe.
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_propriete_statut_exploitation') THEN
        ALTER TABLE propriete DROP CONSTRAINT chk_propriete_statut_exploitation;
    END IF;

    ALTER TABLE propriete ADD CONSTRAINT chk_propriete_statut_exploitation
        CHECK (statut_exploitation IN ('EN_CONSTRUCTION', 'NEUF', 'DEJA_RENTABLE'));
END $$;

-- 2) Date de livraison prevue (utile pour EN_CONSTRUCTION).
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS date_livraison_prevue DATE;

CREATE INDEX IF NOT EXISTS idx_propriete_date_livraison
    ON propriete (date_livraison_prevue)
    WHERE date_livraison_prevue IS NOT NULL;
