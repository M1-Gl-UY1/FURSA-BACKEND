-- Phase 10c ajustement (reunion Hugh 22/05/2026) : seuil de financement 80% -> 100%
-- Modele : FURSA n'achete le bien au proprietaire reel que lorsque TOUTES les parts
-- sont vendues. Le seuil 80% etait une decision interne avant la reunion ; Hugh
-- impose 100% pour garantir la coherence du modele d'acquisition.

-- 1. Aligner toutes les escrows existantes (creees avec seuil 80) vers 100.
UPDATE escrow_propriete SET seuil_pct = 100 WHERE seuil_pct = 80;

-- 2. Changer le defaut de la colonne pour les futures insertions.
ALTER TABLE escrow_propriete ALTER COLUMN seuil_pct SET DEFAULT 100;

-- 3. Re-evaluer le statut FINANCEE : une propriete passee a FINANCEE sous l'ancien
-- seuil 80 mais qui n'a pas atteint 100 doit retourner en EN_COLLECTE pour
-- coherence. Si totalCollecte >= montantCible (calc theorique part * prixUnitaire),
-- on garde FINANCEE.
UPDATE escrow_propriete e
SET statut = 'EN_COLLECTE', financee_le = NULL
FROM propriete p
WHERE e.id_prop = p.id_prop
  AND e.statut = 'FINANCEE'
  AND p.nombre_total_part IS NOT NULL
  AND p.nombre_total_part > 0
  AND e.total_collecte < (CAST(p.nombre_total_part AS NUMERIC) * COALESCE(p.prix_unitaire_part, 0));
