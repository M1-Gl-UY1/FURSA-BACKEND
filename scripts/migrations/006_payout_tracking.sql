-- Phase 9 : tracabilite du payout effectif des dividendes
-- - Revenus : justificatif (preuve du loyer percu) + flag argent recu par FURSA
-- - Dividende : date paiement effectif + preuve + methode paiement

ALTER TABLE revenus ADD COLUMN IF NOT EXISTS justificatif_url VARCHAR(500);
ALTER TABLE revenus ADD COLUMN IF NOT EXISTS argent_recu_par_fursa BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE dividende ADD COLUMN IF NOT EXISTS date_paiement_effectif DATE;
ALTER TABLE dividende ADD COLUMN IF NOT EXISTS preuve_paiement VARCHAR(500);
ALTER TABLE dividende ADD COLUMN IF NOT EXISTS methode_paiement VARCHAR(30);

-- Pour les revenus deja DISTRIBUE en base au moment de la migration, on considere
-- que l'argent a ete recu (compatibilite avec les donnees historiques).
UPDATE revenus SET argent_recu_par_fursa = TRUE WHERE statut = 'DISTRIBUE';
