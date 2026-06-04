-- Migration V2 Phase G.1 (04/06/2026) : equipements admin-configurables.
--
-- Strategie de migration sans rupture :
--   1. Cree la table equipement (registre)
--   2. Cree la table propriete_equipement (liaison many-to-many)
--   3. Seed les 6 equipements historiques pour qu'ils restent affichables
--   4. Backfill : pour chaque bien existant, copie les has_xxx=true dans la
--      table de liaison (zero perte de donnees)
--   5. Les colonnes has_xxx restent en base pendant la transition (compat
--      ascendante avec d'eventuels anciens clients qui lisent encore ces
--      booleens). Elles seront supprimees dans une migration ulterieure.

BEGIN;

-- 1. Table registre des equipements
CREATE TABLE IF NOT EXISTS equipement (
    id_equipement      BIGSERIAL PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL UNIQUE,
    label              VARCHAR(100) NOT NULL,
    icone              VARCHAR(50),
    ordre_affichage    INTEGER      NOT NULL DEFAULT 100,
    actif              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_equipement_actif ON equipement(actif) WHERE actif = TRUE;
CREATE INDEX IF NOT EXISTS idx_equipement_ordre ON equipement(ordre_affichage);

-- 2. Table de liaison
CREATE TABLE IF NOT EXISTS propriete_equipement (
    id_prop            BIGINT NOT NULL,
    id_equipement      BIGINT NOT NULL,
    PRIMARY KEY (id_prop, id_equipement),
    FOREIGN KEY (id_prop) REFERENCES propriete(id_prop) ON DELETE CASCADE,
    FOREIGN KEY (id_equipement) REFERENCES equipement(id_equipement) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_propeq_equip ON propriete_equipement(id_equipement);

-- 3. Seed des equipements historiques (idempotent)
INSERT INTO equipement (code, label, icone, ordre_affichage, actif) VALUES
    ('PISCINE',       'Piscine',       'Waves',      10, TRUE),
    ('CLIMATISATION', 'Climatisation', 'Wind',       20, TRUE),
    ('PARKING',       'Parking',       'Car',        30, TRUE),
    ('ASCENSEUR',     'Ascenseur',     'Building2',  40, TRUE),
    ('JARDIN',        'Jardin',        'Trees',      50, TRUE),
    ('VUE_MER',       'Vue mer',       'Eye',        60, TRUE)
ON CONFLICT (code) DO NOTHING;

-- 4. Backfill : copie les has_xxx existants dans la table de liaison.
-- INSERT ... ON CONFLICT DO NOTHING pour rester idempotent.
INSERT INTO propriete_equipement (id_prop, id_equipement)
SELECT p.id_prop, e.id_equipement
FROM propriete p, equipement e
WHERE e.code = 'PISCINE' AND COALESCE(p.has_piscine, FALSE) = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO propriete_equipement (id_prop, id_equipement)
SELECT p.id_prop, e.id_equipement
FROM propriete p, equipement e
WHERE e.code = 'CLIMATISATION' AND COALESCE(p.has_climatisation, FALSE) = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO propriete_equipement (id_prop, id_equipement)
SELECT p.id_prop, e.id_equipement
FROM propriete p, equipement e
WHERE e.code = 'PARKING' AND COALESCE(p.has_parking, FALSE) = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO propriete_equipement (id_prop, id_equipement)
SELECT p.id_prop, e.id_equipement
FROM propriete p, equipement e
WHERE e.code = 'ASCENSEUR' AND COALESCE(p.has_ascenseur, FALSE) = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO propriete_equipement (id_prop, id_equipement)
SELECT p.id_prop, e.id_equipement
FROM propriete p, equipement e
WHERE e.code = 'JARDIN' AND COALESCE(p.has_jardin, FALSE) = TRUE
ON CONFLICT DO NOTHING;

INSERT INTO propriete_equipement (id_prop, id_equipement)
SELECT p.id_prop, e.id_equipement
FROM propriete p, equipement e
WHERE e.code = 'VUE_MER' AND COALESCE(p.has_vue_mer, FALSE) = TRUE
ON CONFLICT DO NOTHING;

COMMIT;
