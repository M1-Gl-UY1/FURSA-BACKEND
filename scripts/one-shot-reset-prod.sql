-- V2 CC (08/06/2026) : reset complet des donnees metier avant lancement prod.
--
-- ATTENTION : IRREVERSIBLE. A executer une SEULE fois pour passer de la phase
-- demo / test a la phase production reelle. Backups BDD + uploads obligatoires
-- avant execution (cf ~/Fursa/backups/ sur le VPS).
--
-- Garde :
--   - users role=ADMIN (et leur ligne admin)
--   - referentiels : type_bien_ref, equipement_ref, categorie_document_ref,
--                    section_photo_ref, partenaire_gestion, devise_rate, app_setting
--   - schema BDD complet (migrations Flyway)
--
-- Supprime :
--   - toutes les proprietes + documents + photos/videos/pdf
--   - tous les investisseurs (users role=INVESTISSEUR)
--   - toutes les possessions, dividendes, paiements, transactions, annonces,
--     escrows, wallets, retraits, kyc, notifications, listes d'attente,
--     historique prix, queue sync, refresh tokens, sessions paiement

BEGIN;

-- Tronquer toutes les tables business data en cascade.
-- RESTART IDENTITY remet les sequences a 1 → les nouvelles proprietes auront id 1, 2, 3...
TRUNCATE TABLE
    dividende,
    transaction,
    paiement,
    revenus,
    demande_retrait,
    escrow_transaction,
    escrow_propriete,
    annonce,
    possession,
    document,
    historique_prix_part,
    liste_attente,
    wallet_transaction,
    wallet,
    notification,
    kyc_submission,
    payment_session,
    blockchain_sync_queue,
    refresh_tokens
RESTART IDENTITY CASCADE;

-- Propriete + tout ce qui en depend en cascade
TRUNCATE TABLE propriete RESTART IDENTITY CASCADE;

-- Investisseurs : supprimer d'abord les rows enfant (table investisseur),
-- puis les rows parent (table users) sauf admin.
-- Hierarchie JPA JOINED : 1 row users + 1 row investisseur par compte investisseur.
DELETE FROM investisseur;
DELETE FROM users WHERE role = 'INVESTISSEUR';

-- Reset de la sequence users pour repartir propre.
-- Note : les admins gardent leur id_user actuel.
SELECT setval(
    pg_get_serial_sequence('users', 'id_user'),
    COALESCE((SELECT MAX(id_user) FROM users), 1),
    true
);

COMMIT;

-- Verification post-purge
DO $$
DECLARE
    nb_prop INTEGER;
    nb_inv  INTEGER;
    nb_doc  INTEGER;
    nb_adm  INTEGER;
BEGIN
    SELECT COUNT(*) INTO nb_prop FROM propriete;
    SELECT COUNT(*) INTO nb_inv  FROM investisseur;
    SELECT COUNT(*) INTO nb_doc  FROM document;
    SELECT COUNT(*) INTO nb_adm  FROM users WHERE role = 'ADMIN';
    RAISE NOTICE '[reset] APRES purge : proprietes=% | investisseurs=% | documents=% | admins=%',
                 nb_prop, nb_inv, nb_doc, nb_adm;
END $$;
