-- ============================================================================
-- Phase P4 (Hugh 22/05/2026) : modele "FURSA acheteur"
-- ============================================================================
-- "nous on va acheter la majorite, on va acheter nous-meme, on va remettre
--  sur FURSA pour vendre ca en token aux gens" (Hugh, 00:32:07)
--
-- Workflow Paje Square : FURSA (en tant qu'entite juridique) achete les biens
-- one-time au promoteur (CPS Africa), puis remet ces biens sur la plateforme
-- en tokenisation pour les investisseurs.
--
-- Option A (la plus simple, decision user 25/05) : pas de role utilisateur
-- specifique pour FURSA. Un flag `acquis_fursa` sur la propriete suffit
-- pour distinguer ces biens des biens proposes par des proprietaires
-- individuels. Affichage frontend : badge "Acquis FURSA".
-- ============================================================================

ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS acquis_fursa BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_propriete_acquis_fursa
    ON propriete (acquis_fursa)
    WHERE acquis_fursa = TRUE;
