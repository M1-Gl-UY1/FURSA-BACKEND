-- Migration V2 Phase G.3 (04/06/2026) : types de bien admin-configurables.
--
-- Strategie additive zero-rupture :
--   1. Cree la table type_bien (registre)
--   2. Seed les 7 types historiques de l'enum TypeBien (avec leurs meta)
--   3. Ajoute propriete.type_bien_code (string) en PARALLELE de la colonne
--      type_bien existante (qui reste un enum). Source de verite future =
--      type_bien_code ; type_bien reste pour retro-compat lecture.
--   4. Backfill : copie type_bien -> type_bien_code (valeurs identiques car
--      @Enumerated(STRING) stocke deja la string du code).
--
-- L'admin pourra ensuite creer LOFT, MAISON_DE_VILLE, TERRAIN, BUREAU, etc.
-- Pour ces codes custom, propriete.type_bien restera null (l'enum n'en a pas
-- connaissance) et propriete.type_bien_code portera la valeur.

BEGIN;

-- 1. Table registre des types de bien
CREATE TABLE IF NOT EXISTS type_bien (
    id_type_bien       BIGSERIAL PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL UNIQUE,
    label              VARCHAR(100) NOT NULL,
    icone              VARCHAR(50),
    ordre_affichage    INTEGER      NOT NULL DEFAULT 100,
    actif              BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Pilote l'affichage du champ "Nb chambres" dans le wizard :
    -- false pour STUDIO et CHAMBRE (logique historique hardcodee jusqu'ici).
    exige_chambres     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_type_bien_actif ON type_bien(actif) WHERE actif = TRUE;
CREATE INDEX IF NOT EXISTS idx_type_bien_ordre ON type_bien(ordre_affichage);

-- 2. Seed des 7 types historiques (idempotent)
INSERT INTO type_bien (code, label, icone, ordre_affichage, actif, exige_chambres) VALUES
    ('VILLA',       'Villa',       'Castle',     10, TRUE, TRUE),
    ('APPARTEMENT', 'Appartement', 'Building',   20, TRUE, TRUE),
    ('STUDIO',      'Studio',      'Home',       30, TRUE, FALSE),
    ('PENTHOUSE',   'Penthouse',   'Sparkles',   40, TRUE, TRUE),
    ('DUPLEX',      'Duplex',      'Building2',  50, TRUE, TRUE),
    ('IMMEUBLE',    'Immeuble',    'Building2',  60, TRUE, TRUE),
    ('CHAMBRE',     'Chambre',     'BedDouble',  70, TRUE, FALSE)
ON CONFLICT (code) DO NOTHING;

-- 3. Colonne propriete.type_bien_code en parallele de type_bien (enum)
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS type_bien_code VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_propriete_type_bien_code
    ON propriete(type_bien_code);

-- 4. Backfill : pour les biens existants, copie type_bien -> type_bien_code.
-- @Enumerated(STRING) stocke deja la string ("VILLA", "APPARTEMENT", ...) en
-- BDD, donc l'egalite est directe. Idempotent : ne touche que les lignes ou
-- type_bien_code est null.
UPDATE propriete
SET type_bien_code = type_bien
WHERE type_bien_code IS NULL AND type_bien IS NOT NULL;

COMMIT;
