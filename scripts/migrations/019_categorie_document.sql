-- ============================================================================
-- Phase P8 (Hugh 22/05/2026) : typage des documents legaux
-- ============================================================================
-- "on va demander les documents qui vont prouver que effectivement l'appartement
--  la est rentable tel que soit un contrat de bail, soit des releves qui viennent
--  de Airbnb si c'est que c'etait exploite sur Airbnb ou bien d'un autre
--  partenaire" (Hugh, 00:42:06)
--
-- Permet a l'admin de voir le type de preuve fournie (contrat de bail, releve
-- Airbnb, titre foncier, etc.) au lieu d'un simple "PDF generique".
-- ============================================================================

ALTER TABLE document
    ADD COLUMN IF NOT EXISTS categorie_document VARCHAR(30);

-- Pour les documents existants, on laisse NULL : le frontend les affichera
-- comme "non typee" en attendant un re-classement manuel par l'admin si besoin.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_document_categorie') THEN
        ALTER TABLE document ADD CONSTRAINT chk_document_categorie
            CHECK (categorie_document IS NULL OR categorie_document IN (
                'TITRE_FONCIER',
                'PERMIS_CONSTRUIRE',
                'CONTRAT_GESTION',
                'CONTRAT_BAIL',
                'RELEVE_AIRBNB',
                'AUTRE'
            ));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_document_categorie
    ON document (categorie_document)
    WHERE categorie_document IS NOT NULL;
