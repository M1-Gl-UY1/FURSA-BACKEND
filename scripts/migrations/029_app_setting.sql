-- Migration V2 Phase G.5 (05/06/2026) : settings application admin-configurables.
--
-- Table generique cle/valeur/type pour exposer des parametres modifiables par
-- l'admin sans redeploiement. Les services backend lisent ces valeurs au lieu
-- des constantes hardcodees.
--
-- En V1 on expose :
--   - file.max_size_pdf_mo       (10)
--   - file.max_size_image_mo     (4)
--   - file.max_size_video_mo     (100)
--   - kyc.age_minimum            (18)
--   - kyc.age_maximum            (100)
--   - declaration.fenetre_jours  (5)   : fenetre de declaration revenu apres fin de mois
--   - escrow.seuil_pct_defaut    (100) : seuil de financement par defaut a la creation
--
-- Strategie zero-rupture : tant que la table n'existe pas ou qu'une cle n'est
-- pas renseignee, le service fallback sur la valeur par defaut hardcodee dans
-- le code Java (qui devient la valeur de seed).

BEGIN;

CREATE TABLE IF NOT EXISTS app_setting (
    cle              VARCHAR(100) PRIMARY KEY,
    valeur           TEXT         NOT NULL,
    -- Type de la valeur (INTEGER, LONG, DECIMAL, BOOLEAN, STRING)
    type             VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    label            VARCHAR(200) NOT NULL,
    description      TEXT,
    -- Groupe d'affichage dans la page admin (Files, KYC, Escrow, ...)
    groupe           VARCHAR(50)  NOT NULL DEFAULT 'AUTRE',
    -- Unite affichee a cote de la valeur (Mo, ans, jours, %, etc.)
    unite            VARCHAR(20),
    ordre_affichage  INTEGER      NOT NULL DEFAULT 100,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_setting_groupe ON app_setting(groupe);
CREATE INDEX IF NOT EXISTS idx_app_setting_ordre ON app_setting(ordre_affichage);

-- Seed des settings de base. ON CONFLICT DO NOTHING pour rester idempotent
-- (les valeurs deja modifiees par l'admin ne sont pas ecrasees).
INSERT INTO app_setting (cle, valeur, type, label, description, groupe, unite, ordre_affichage) VALUES
    ('file.max_size_pdf_mo',       '10',  'INTEGER',
        'Taille max d''un PDF',
        'Taille maximale (en Mo) d''un document PDF uploade (titre foncier, contrats, etc.).',
        'Fichiers', 'Mo', 10),

    ('file.max_size_image_mo',     '4',   'INTEGER',
        'Taille max d''une image',
        'Taille maximale (en Mo) d''une photo de bien (JPEG, PNG, WEBP).',
        'Fichiers', 'Mo', 20),

    ('file.max_size_video_mo',     '100', 'INTEGER',
        'Taille max d''une video',
        'Taille maximale (en Mo) de la video de visite guidee.',
        'Fichiers', 'Mo', 30),

    ('kyc.age_minimum',            '18',  'INTEGER',
        'Age minimum pour la verification d''identite',
        'Un investisseur doit avoir au moins cet age pour creer un compte et investir.',
        'Verification d''identite', 'ans', 10),

    ('kyc.age_maximum',            '100', 'INTEGER',
        'Age maximum pour la verification d''identite',
        'Plafond raisonnable au-dela duquel la verification est refusee (sanity check).',
        'Verification d''identite', 'ans', 20),

    ('declaration.fenetre_jours',  '5',   'INTEGER',
        'Fenetre de declaration revenu',
        'Nombre de jours apres la fin du mois pendant lesquels le proprietaire peut declarer les revenus sans penalite.',
        'Revenus', 'jours', 10),

    ('escrow.seuil_pct_defaut',    '100', 'INTEGER',
        'Seuil de financement escrow par defaut',
        'Pourcentage de financement requis a la creation d''un bien pour declencher la cloture de l''escrow.',
        'Escrow', '%', 10)
ON CONFLICT (cle) DO NOTHING;

COMMIT;
