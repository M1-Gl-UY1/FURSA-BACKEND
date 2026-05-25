-- ============================================================================
-- Phase P2 (Hugh 22/05/2026) : liste d'attente sur un bien finance
-- ============================================================================
-- "on peut mettre un systeme de liste d'attente. Les gens viennent s'enregistrer
--  sur des systemes de liste d'attente pour acquerir des parts d'un bien. Et
--  plus il y a de personnes qui sollicitent le bien, plus le prix de la part
--  va augmenter." (Hugh, 00:37:16)
--
-- Une inscription = un investisseur qui veut acquerir X parts d'un bien
-- entierement vendu. Quand une part redevient dispo (revente sur marche
-- secondaire), le premier de la file FIFO est notifie.
--
-- Le total des parts en attente alimente directement le bonus_demande du
-- mecanisme de prix dynamique (voir PRIX_DYNAMIQUE_FURSA.md §4).
-- ============================================================================

CREATE TABLE IF NOT EXISTS liste_attente (
    id BIGSERIAL PRIMARY KEY,
    id_prop BIGINT NOT NULL REFERENCES propriete(id_prop) ON DELETE CASCADE,
    id_inv BIGINT NOT NULL,

    -- Nombre de parts que l'investisseur souhaite acquerir
    nombre_parts INTEGER NOT NULL CHECK (nombre_parts >= 1 AND nombre_parts <= 100),

    -- Workflow
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    -- EN_ATTENTE : dans la file
    -- NOTIFIE    : une part est dispo, l'investisseur a ete prevenu (non utilise V1)
    -- SERVI      : l'investisseur a achete au moins une part suite a sa notif
    -- ANNULE     : retire par l'investisseur

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notifie_le TIMESTAMP,
    servi_le TIMESTAMP,

    CONSTRAINT chk_liste_attente_statut
        CHECK (statut IN ('EN_ATTENTE', 'NOTIFIE', 'SERVI', 'ANNULE')),

    -- Un investisseur ne peut avoir qu'UNE seule inscription ACTIVE par bien.
    -- (les inscriptions ANNULE/SERVI peuvent coexister avec une nouvelle EN_ATTENTE).
    CONSTRAINT uk_liste_attente_inv_prop_active
        UNIQUE (id_prop, id_inv, statut) DEFERRABLE INITIALLY DEFERRED
);

-- Index pour les requetes FIFO (qui est le premier ?)
CREATE INDEX IF NOT EXISTS idx_liste_attente_fifo
    ON liste_attente (id_prop, statut, created_at);

-- Index pour la consultation utilisateur ("ma liste d'attente")
CREATE INDEX IF NOT EXISTS idx_liste_attente_inv
    ON liste_attente (id_inv, statut, created_at DESC);
