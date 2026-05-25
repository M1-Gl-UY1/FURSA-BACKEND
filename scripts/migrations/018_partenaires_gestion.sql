-- ============================================================================
-- Phase P9 (Hugh 22/05/2026) : delegation gestion locative a un partenaire
-- ============================================================================
-- "on peut confier le bien a Airbnb, on peut utiliser plein de partenaires qui
--  vont gerer l'aspect location et entretien du bien" (Hugh, 00:35:59)
--
-- Chaque bien peut etre assigne a un partenaire gestionnaire (operation locative,
-- entretien, relation locataires). L'investisseur voit "Gere par {partenaire}"
-- sur la fiche bien -> rassurance + professionnalisme.
-- ============================================================================

CREATE TABLE IF NOT EXISTS partenaire_gestion (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(150) NOT NULL UNIQUE,
    type_partenaire VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    site_web VARCHAR(300),
    contact_email VARCHAR(150),
    logo_url VARCHAR(500),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_partenaire_type
        CHECK (type_partenaire IN (
            'GESTION_LOCATIVE',
            'PROMOTEUR_VENTE',
            'BLOCKCHAIN_OPS',
            'EXPERTISE_LOCALE'
        ))
);

CREATE INDEX IF NOT EXISTS idx_partenaire_actif_type
    ON partenaire_gestion (actif, type_partenaire);

-- FK optionnelle sur propriete : qui gere ce bien ?
ALTER TABLE propriete
    ADD COLUMN IF NOT EXISTS gestionnaire_id BIGINT REFERENCES partenaire_gestion(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_propriete_gestionnaire
    ON propriete (gestionnaire_id);

-- Seed des partenaires officiels mentionnes par Hugh + pitch officiel.
-- Reference : pitch FURSA Community page 12 + reunion 22/05/2026.
INSERT INTO partenaire_gestion (nom, type_partenaire, description, site_web, actif)
VALUES
    ('CPS Zanzibar',
     'GESTION_LOCATIVE',
     'Gestion immobiliere et operations locataires. Maintient 95% occupation en haute saison.',
     NULL,
     TRUE),
    ('Airbnb',
     'GESTION_LOCATIVE',
     'Plateforme de location courte duree. Visibilite internationale, processus standardise.',
     'https://www.airbnb.com',
     TRUE),
    ('Paje Square',
     'PROMOTEUR_VENTE',
     'Promoteur de residences contemporaines a Paje. Partenaire principal de FURSA.',
     'https://www.pajesquare.com',
     TRUE),
    ('Fumba Town',
     'PROMOTEUR_VENTE',
     'Ville nouvelle ecoresponsable concue par CPS Africa.',
     'https://fumba.town',
     TRUE),
    ('Africa Bahari',
     'EXPERTISE_LOCALE',
     'Expertise immobiliere locale sur Zanzibar et l''Afrique de l''Est.',
     NULL,
     TRUE),
    ('SEED Innov',
     'BLOCKCHAIN_OPS',
     'Infrastructure blockchain et rails de paiement securises.',
     NULL,
     TRUE)
ON CONFLICT (nom) DO NOTHING;
