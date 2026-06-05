-- Migration V2 Phase G.4 (05/06/2026) : sections photos admin-configurables.
--
-- Strategie additive zero-rupture :
--   1. Cree la table section_photo (registre + meta + flag requise)
--   2. Seed les 9 codes historiques avec leur configuration actuelle
--   3. Ajoute document.section_photo_code (string) en PARALLELE de la
--      colonne document.section_photo existante (qui reste un enum stocke
--      en VARCHAR(20)).
--   4. Backfill : copie section_photo -> section_photo_code.
--
-- L'admin pourra ensuite creer TERRASSE, GARAGE, BALCON, CAVE, BUREAU_INTERIEUR,
-- SALLE_DE_SPORT, ROOFTOP, etc.
--
-- Pour V1, les regles conditionnelles ("CHAMBRE si typeBien.exigeChambres",
-- "PISCINE si equipement PISCINE", "VUE si equipement VUE_MER") restent
-- hardcodees dans le wizard. Le flag {@code requise} permet de marquer une
-- section comme toujours obligatoire (FACADE et SALON).

BEGIN;

-- 1. Table registre des sections photos
CREATE TABLE IF NOT EXISTS section_photo (
    id_section_photo   BIGSERIAL PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL UNIQUE,
    label              VARCHAR(100) NOT NULL,
    icone              VARCHAR(50),
    ordre_affichage    INTEGER      NOT NULL DEFAULT 100,
    actif              BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Toujours requise dans le wizard (FACADE et SALON par defaut).
    -- Les codes custom de l'admin sont OPTIONNELS par defaut.
    requise            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_section_photo_actif ON section_photo(actif) WHERE actif = TRUE;
CREATE INDEX IF NOT EXISTS idx_section_photo_ordre ON section_photo(ordre_affichage);

-- 2. Seed des 9 codes historiques
INSERT INTO section_photo (code, label, icone, ordre_affichage, actif, requise) VALUES
    ('FACADE',        'Façade avant',       'Camera',     10, TRUE, TRUE),
    ('SALON',         'Salon',              'Sofa',       20, TRUE, TRUE),
    ('CUISINE',       'Cuisine',            'ChefHat',    30, TRUE, FALSE),
    ('CHAMBRE',       'Chambres',           'Bed',        40, TRUE, FALSE),
    ('SALLE_DE_BAIN', 'Salle de bain',      'Bath',       50, TRUE, FALSE),
    ('PISCINE',       'Piscine',            'Waves',      60, TRUE, FALSE),
    ('EXTERIEUR',     'Extérieur / jardin', 'Trees',      70, TRUE, FALSE),
    ('VUE',           'Vue',                'Eye',        80, TRUE, FALSE),
    ('AUTRE',         'Autres photos',      'Image',      90, TRUE, FALSE)
ON CONFLICT (code) DO NOTHING;

-- 3. Ajoute la colonne code en parallele de l'enum existant
ALTER TABLE document
    ADD COLUMN IF NOT EXISTS section_photo_code VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_document_section_photo_code
    ON document(section_photo_code);

-- 4. Backfill : copie l'enum string vers le code string.
-- @Enumerated(STRING) stocke deja la string ("FACADE", "SALON", ...) en BDD,
-- donc l'egalite est directe. Idempotent.
UPDATE document
SET section_photo_code = section_photo
WHERE section_photo_code IS NULL
  AND section_photo IS NOT NULL;

COMMIT;
