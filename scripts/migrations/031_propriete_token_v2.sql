-- V2 O (07/06/2026) : ProprieteTokenV2 — prix dynamique on-chain.
--
-- Ajoute une colonne contrat_version qui permet de distinguer les biens
-- tokenises avec le contrat V1 immuable (legacy) de ceux deployes avec V2
-- (prix mutable + bonus + statut sync via BlockchainSyncService).
--
-- Migration additive et idempotente :
--   - Tous les biens existants restent en V1 (aucun sync force).
--   - Les nouveaux deploiements basculent en V2 par defaut au niveau service.

ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS contrat_version VARCHAR(8);

-- Les biens deja tokenises (adresse_contrat non null) sont taggues V1.
UPDATE propriete
SET contrat_version = 'V1'
WHERE adresse_contrat IS NOT NULL
  AND contrat_version IS NULL;

-- Les nouveaux biens (sans contrat encore) heriteront du defaut V2 cote service.

-- Index pour pouvoir lister rapidement les biens V2 (audit, dashboard admin).
CREATE INDEX IF NOT EXISTS idx_propriete_contrat_version
    ON propriete (contrat_version);
