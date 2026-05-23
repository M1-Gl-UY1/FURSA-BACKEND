-- Phase Certification (Hugh 22/05/2026) : etape de certification separee
-- post-creation. Le proprio upload ses documents legaux (titre foncier, etc.),
-- l'admin valide, et le bien devient achetable.

-- Etat de certification du bien (independant du statut de publication).
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS statut_certif VARCHAR(20) NOT NULL DEFAULT 'NON_CERTIFIE';

-- Quand le proprio a soumis la demande de certification (uploads + click).
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS certif_soumise_le TIMESTAMP;

-- Motif du refus de certification par l'admin.
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS certif_motif_refus VARCHAR(500);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_propriete_statut_certif') THEN
        ALTER TABLE propriete ADD CONSTRAINT chk_propriete_statut_certif
            CHECK (statut_certif IN ('NON_CERTIFIE', 'EN_REVIEW', 'CERTIFIE', 'REFUSEE'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_propriete_statut_certif ON propriete (statut_certif);

-- Alignement avec colonne booleenne legacy "certifie" : si certifie=true,
-- forcer statut_certif=CERTIFIE pour les donnees existantes.
UPDATE propriete SET statut_certif = 'CERTIFIE' WHERE certifie = TRUE;
