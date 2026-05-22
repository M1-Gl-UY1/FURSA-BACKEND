-- Soft delete users : ajouter la colonne deleted_at
-- Les users avec deleted_at != null sont consideres supprimes :
--   - Ne peuvent plus se logger (CustomUserService verifie)
--   - N'apparaissent plus dans les listes admin par defaut
--   - Leurs transactions/possessions/paiements restent en DB pour audit + compta

ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Index partiel pour accelerer le filtrage des users actifs
CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NOT NULL;
