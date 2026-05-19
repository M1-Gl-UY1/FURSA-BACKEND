-- Migration idempotente : colonnes manquantes en prod sur propriete et revenus
-- Hibernate ddl-auto=validate refuse de demarrer si une colonne mappee en entite
-- n'existe pas en DB. Phases 7/8/blockchain ajoutent des champs Java sans migration SQL.

-- Phase blockchain : tokenisation propriete
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS adresse_contrat VARCHAR(255);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS transaction_hash VARCHAR(255);

-- Phase 7 : workflow soumission propriete
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS proposeur_id BIGINT;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS motif_refus TEXT;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS soumise_le TIMESTAMP;

-- Phase 8 : workflow declaration revenus
ALTER TABLE revenus ADD COLUMN IF NOT EXISTS proposeur_id BIGINT;
ALTER TABLE revenus ADD COLUMN IF NOT EXISTS statut VARCHAR(255);
ALTER TABLE revenus ADD COLUMN IF NOT EXISTS motif_refus TEXT;
ALTER TABLE revenus ADD COLUMN IF NOT EXISTS periode_debut DATE;
ALTER TABLE revenus ADD COLUMN IF NOT EXISTS periode_fin DATE;
