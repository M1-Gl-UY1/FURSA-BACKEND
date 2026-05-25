-- ============================================================================
-- Phase P5 (Hugh 22/05/2026) : conversion devise locale -> USD
-- ============================================================================
-- "Monnaie de base c'est le dollar. Donc celui qui est en train de poster va
--  poster son truc en France et derriere on va convertir tout en dollar. La
--  devise de base, on va travailler avec le dollar. Mais on garde le prix dans
--  sa devise locale, ce que le promoteur du bien a mis." (Hugh, 00:39:36)
--
-- A la soumission, le backend convertit prix_vente_total (devise locale) et
-- prix_unitaire_part (saisi en devise locale via le wizard) en USD via la
-- table devise_rate. La devise locale est conservee pour info sur la fiche.
-- ============================================================================

-- prix_vente_total reste en devise locale (champ existant).
-- On ajoute son equivalent USD pour l'affichage de reference.
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS prix_vente_total_usd NUMERIC(15, 2);

-- Pour les biens existants : si deja en USD, copier; sinon laisser NULL,
-- le service appliquera la conversion la prochaine fois.
UPDATE propriete
    SET prix_vente_total_usd = prix_vente_total
    WHERE prix_vente_total_usd IS NULL
      AND (devise_locale IS NULL OR UPPER(devise_locale) = 'USD');
