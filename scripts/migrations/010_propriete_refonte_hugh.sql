-- P1 (reunion Hugh 22/05/2026) : refonte de la fiche bien.
-- Hugh exige : pays/ville (dropdowns), type de bien dynamique, caracteristiques
-- equipements, photos structurees par section, video visite guidee, statut
-- d'exploitation (neuf vs deja rentable) avec preuves, prix de vente + devise
-- locale + fraction a vendre.

-- ============================================================================
-- 1. Propriete : nouveaux champs
-- ============================================================================
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS pays VARCHAR(2);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS ville VARCHAR(100);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS adresse_precise VARCHAR(300);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS type_bien VARCHAR(20);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS nombre_pieces INTEGER;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS nombre_chambres INTEGER;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS superficie_m2 INTEGER;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS has_piscine BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS has_climatisation BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS has_parking BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS has_ascenseur BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS has_jardin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS has_vue_mer BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS statut_exploitation VARCHAR(20) NOT NULL DEFAULT 'NEUF';
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS revenu_mensuel_actuel NUMERIC(15, 2);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS source_revenu VARCHAR(20);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS prix_vente_total NUMERIC(15, 2);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS devise_locale VARCHAR(3);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS fraction_vendue_pct INTEGER NOT NULL DEFAULT 100;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS video_url VARCHAR(500);
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS certifie BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE propriete ADD COLUMN IF NOT EXISTS certifie_le TIMESTAMP;

-- Sanity checks
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_propriete_fraction_vendue') THEN
        ALTER TABLE propriete ADD CONSTRAINT chk_propriete_fraction_vendue
            CHECK (fraction_vendue_pct BETWEEN 1 AND 100);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_propriete_statut_exploitation') THEN
        ALTER TABLE propriete ADD CONSTRAINT chk_propriete_statut_exploitation
            CHECK (statut_exploitation IN ('NEUF', 'DEJA_RENTABLE'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_propriete_type_bien') THEN
        ALTER TABLE propriete ADD CONSTRAINT chk_propriete_type_bien
            CHECK (type_bien IS NULL OR type_bien IN (
                'VILLA', 'APPARTEMENT', 'STUDIO', 'PENTHOUSE',
                'DUPLEX', 'IMMEUBLE', 'CHAMBRE'
            ));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_propriete_pays_ville ON propriete (pays, ville);
CREATE INDEX IF NOT EXISTS idx_propriete_type_bien ON propriete (type_bien);
CREATE INDEX IF NOT EXISTS idx_propriete_certifie ON propriete (certifie) WHERE certifie = TRUE;

-- ============================================================================
-- 2. Photos structurees par section
-- ============================================================================
-- On garde la table document existante pour les fichiers techniques (PDFs legaux).
-- On ajoute une colonne "section" pour les photos categorisees.
ALTER TABLE document ADD COLUMN IF NOT EXISTS section_photo VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_document_section_photo') THEN
        ALTER TABLE document ADD CONSTRAINT chk_document_section_photo
            CHECK (section_photo IS NULL OR section_photo IN (
                'FACADE', 'SALON', 'CUISINE', 'CHAMBRE', 'SALLE_DE_BAIN',
                'PISCINE', 'EXTERIEUR', 'VUE', 'AUTRE'
            ));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_document_section_photo ON document (section_photo);

-- ============================================================================
-- 3. Donnees retroactives : pour les biens existants, on met TZ/Zanzibar par
-- defaut (cible principale FURSA) et type_bien=APPARTEMENT (cas le plus frequent).
-- A re-editer manuellement si besoin par l'admin via /admin/proprietes/{id}.
-- ============================================================================
UPDATE propriete SET pays = 'TZ' WHERE pays IS NULL;
UPDATE propriete SET ville = 'Zanzibar' WHERE ville IS NULL;
UPDATE propriete SET type_bien = 'APPARTEMENT' WHERE type_bien IS NULL;
UPDATE propriete SET devise_locale = 'USD' WHERE devise_locale IS NULL;
