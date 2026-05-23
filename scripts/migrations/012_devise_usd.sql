-- Migration EUR -> USD (reunion Hugh 22/05/2026)
-- Hugh impose USD comme devise de base pour FURSA. La saisie en devise locale
-- reste possible cote formulaire bien (XAF, EUR, etc.) avec conversion auto.

-- ============================================================================
-- 1. Wallet : assouplir la contrainte devise puis convertir EUR -> USD
-- ============================================================================
-- Drop l'ancienne contrainte EUR-only
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_wallet_devise') THEN
        ALTER TABLE wallet DROP CONSTRAINT chk_wallet_devise;
    END IF;
END $$;

-- Nouvelle contrainte : USD ou EUR (transition phase)
ALTER TABLE wallet ADD CONSTRAINT chk_wallet_devise
    CHECK (devise IN ('USD', 'EUR'));

-- Convertir les wallets existants EUR -> USD.
-- Taux indicatif : 1 EUR ≈ 1.08 USD (utilise un taux moyen 1.08 pour la conversion).
-- Pour MVP les wallets sont vides (solde 0), donc la conversion n'a pas d'impact
-- monetaire reel. Si des soldes existent, le DeviseRateService doit etre utilise
-- en prod pour une conversion precise.
UPDATE wallet
SET solde = ROUND(solde * 1.08, 2),
    devise = 'USD',
    updated_at = CURRENT_TIMESTAMP
WHERE devise = 'EUR';

-- Mettre a jour le defaut pour les futures insertions
ALTER TABLE wallet ALTER COLUMN devise SET DEFAULT 'USD';

-- ============================================================================
-- 2. EscrowPropriete : pas de colonne devise (implicite EUR/USD via le bien).
-- Rien a migrer ici, le solde est traite comme USD a partir de maintenant.
-- ============================================================================
