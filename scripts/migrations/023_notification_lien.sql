-- ============================================================================
-- Phase Notifications V2 (Hugh) : lien cliquable + broadcasts
-- ============================================================================
-- Chaque notification peut maintenant porter un lien (ex /opportunites/12)
-- qui sert de cible au clic dans le centre de notifications.
--
-- Pas de FK : on stocke un chemin relatif (frontend route), facile a faire
-- evoluer sans schema change.
-- ============================================================================

ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS lien VARCHAR(500);

-- Pour les notifications existantes : pas de lien (NULL). Le clic ouvrira juste
-- la page d'index /notifications ou marquera lue, sans navigation specifique.
