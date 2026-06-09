-- V2 HH (09/06/2026) : ajout des 4 types wallet_transaction manquants.
--
-- Bug : la phase V2 Z (workflow wallet-to-escrow pour declaration de revenus)
-- a ajoute 4 valeurs dans l'enum Java TypeWalletTransaction (DEBIT_DECLARATION_REVENU,
-- CREDIT_DECLARATION_REVENU, CREDIT_REFUND_DECLARATION, DEBIT_DISTRIBUTION_REVENU)
-- mais la contrainte CHECK Postgres "chk_wallettx_type" n'a pas ete mise a jour.
-- Resultat : 409 Conflict des qu'on tente une declaration de revenu (le INSERT
-- echoue avec "violates check constraint chk_wallettx_type").
--
-- Cette migration drop+recreate la contrainte avec les 13 valeurs actuelles.
-- Idempotent : DROP IF EXISTS + ADD.

ALTER TABLE wallet_transaction DROP CONSTRAINT IF EXISTS chk_wallettx_type;

ALTER TABLE wallet_transaction ADD CONSTRAINT chk_wallettx_type CHECK (
    type IN (
        'TOPUP',
        'DEBIT_ACHAT_PARTS',
        'DEBIT_ACHAT_REVENTE',
        'CREDIT_DIVIDENDE',
        'CREDIT_VENTE_PARTS',
        'CREDIT_REVENTE',
        'CREDIT_REFUND_ACHAT',
        'DEBIT_WITHDRAW',
        'AJUSTEMENT_ADMIN',
        'DEBIT_DECLARATION_REVENU',
        'CREDIT_DECLARATION_REVENU',
        'CREDIT_REFUND_DECLARATION',
        'DEBIT_DISTRIBUTION_REVENU'
    )
);

DO $$ BEGIN
    RAISE NOTICE '[migration 035] chk_wallettx_type mis a jour avec 13 valeurs (4 ajoutees pour V2 Z workflow)';
END $$;
