-- Migration V2 Phase G.2 (04/06/2026) : categories de document admin-configurables.
--
-- Strategie additive zero-rupture :
--   1. Cree la table categorie_document (registre + meta + regle d'obligation)
--   2. Seed les 6 codes historiques avec leur regle d'obligation actuelle
--   3. Ajoute document.categorie_document_code (string) en PARALLELE de la
--      colonne document.categorie_document existante (qui reste un enum stocke
--      en VARCHAR(30) avec un CHECK constraint).
--   4. Backfill : copie categorie_document -> categorie_document_code.
--   5. Drop le CHECK constraint chk_document_categorie pour permettre les
--      codes custom (ex ASSURANCE_HABITATION, DIAGNOSTIC_DPE, ACTE_NOTARIE).
--      L'integrite reste assuree par le frontend (dropdown du catalogue)
--      et la logique de validation cote service.
--
-- IMPORTANT : la regle d'obligation est stockee dans la table mais N'EST PAS
-- encore appliquee dynamiquement par le service (la validation de finalisation
-- reste hardcodee pour les 6 codes historiques afin de preserver la regle
-- metier actuelle). Cette colonne est preparee pour une refonte ulterieure
-- de la validation. Les codes custom sont OPTIONNEL par defaut.

BEGIN;

-- 1. Table registre des categories de document
CREATE TABLE IF NOT EXISTS categorie_document (
    id_categorie_doc   BIGSERIAL PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL UNIQUE,
    label              VARCHAR(100) NOT NULL,
    description        VARCHAR(500),
    icone              VARCHAR(50),
    ordre_affichage    INTEGER      NOT NULL DEFAULT 100,
    actif              BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Regle d'obligation :
    --   'TOUJOURS'              = obligatoire pour tous les biens
    --   'SI_NEUF_OU_CONSTRUCTION' = obligatoire si statut NEUF ou EN_CONSTRUCTION
    --   'SI_DEJA_RENTABLE'      = obligatoire si statut DEJA_RENTABLE
    --                              (groupe : au moins un parmi les cats avec
    --                              cette regle suffit)
    --   'OPTIONNEL'             = jamais obligatoire (defaut pour custom)
    regle_obligation   VARCHAR(30)  NOT NULL DEFAULT 'OPTIONNEL',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cat_doc_actif ON categorie_document(actif) WHERE actif = TRUE;
CREATE INDEX IF NOT EXISTS idx_cat_doc_ordre ON categorie_document(ordre_affichage);

-- 2. Seed des 6 codes historiques avec leur regle actuelle
INSERT INTO categorie_document (code, label, description, icone, ordre_affichage, actif, regle_obligation) VALUES
    ('TITRE_FONCIER',     'Titre foncier',
        'Preuve officielle de propriete (obligatoire pour tous les biens).',
        'FileText', 10, TRUE, 'TOUJOURS'),
    ('PERMIS_CONSTRUIRE', 'Permis de construire',
        'Certificat de conformite, obligatoire pour les biens neufs ou en construction.',
        'FileText', 20, TRUE, 'SI_NEUF_OU_CONSTRUCTION'),
    ('CONTRAT_GESTION',   'Contrat de gestion locative',
        'Mandat avec un partenaire de gestion (TOC, Airbnb Pro, etc.). Pour biens deja rentables.',
        'FileText', 30, TRUE, 'SI_DEJA_RENTABLE'),
    ('CONTRAT_BAIL',      'Contrat de bail',
        'Preuve d''un revenu locatif long terme. Pour biens deja rentables.',
        'FileText', 40, TRUE, 'SI_DEJA_RENTABLE'),
    ('RELEVE_AIRBNB',     'Releve Airbnb / plateforme',
        'Justificatif de revenus locatifs courte duree (Airbnb, Booking, etc.).',
        'FileText', 50, TRUE, 'OPTIONNEL'),
    ('AUTRE',             'Autre document',
        'Tout autre document utile : expertise, plans, attestation, etc.',
        'FileText', 90, TRUE, 'OPTIONNEL')
ON CONFLICT (code) DO NOTHING;

-- 3. Ajoute la colonne code en parallele de l'enum existant
ALTER TABLE document
    ADD COLUMN IF NOT EXISTS categorie_document_code VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_document_categorie_code
    ON document(categorie_document_code);

-- 4. Backfill : copie l'enum string vers le code string.
-- @Enumerated(STRING) stocke deja la string ("TITRE_FONCIER", ...) en BDD,
-- donc l'egalite est directe. Idempotent.
UPDATE document
SET categorie_document_code = categorie_document
WHERE categorie_document_code IS NULL
  AND categorie_document IS NOT NULL;

-- 5. Drop le CHECK constraint qui empechait les codes custom.
-- L'integrite est desormais assuree par le service (resolution via le
-- registre categorie_document) et le frontend (dropdown).
ALTER TABLE document DROP CONSTRAINT IF EXISTS chk_document_categorie;

COMMIT;
