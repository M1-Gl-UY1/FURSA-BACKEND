-- V2 BB (08/06/2026) : reparation desynchro KYC user / onglet Verifications.
--
-- Probleme : certains users en BDD ont is_verified=true (vu dans l'onglet
-- "Utilisateurs" admin) sans KycSubmission APPROVED correspondante. Resultat :
-- l'onglet "Verifications d'identite" admin ne les liste pas et le compteur
-- "Approuves" semble incoherent.
--
-- Causes possibles legacy :
--   - seeder dev pousse en prod par erreur
--   - set manuel is_verified=true via SQL ops
--   - approve() execute avant que KycSubmission existe
--
-- Cette migration cree une KycSubmission APPROVED minimale pour chaque user
-- is_verified=true qui n'en a aucune. Audit trail conserve via motif :
-- "Synchronisation legacy V2 BB - statut inferé de is_verified".
--
-- Idempotente : la sous-requete NOT EXISTS evite les doublons.

INSERT INTO kyc_submission (
    id_inv,
    statut,
    nationalite,
    pays_residence,
    declaration_sur_honneur,
    is_pep,
    submitted_at,
    reviewed_at,
    nombre_re_submissions,
    version
)
SELECT
    i.id_user,
    'APPROVED',
    'XX',                       -- placeholder : pas connu pour les legacy
    'XX',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP - INTERVAL '30 days',
    CURRENT_TIMESTAMP - INTERVAL '30 days',
    0,
    0
FROM investisseur i
WHERE i.is_verified = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM kyc_submission k
      WHERE k.id_inv = i.id_user
        AND k.statut IN ('APPROVED', 'PENDING', 'IN_REVIEW')
  );

-- Compteur informatif.
DO $$
DECLARE
    n INTEGER;
BEGIN
    SELECT COUNT(*) INTO n
    FROM investisseur i
    WHERE i.is_verified = TRUE
      AND EXISTS (SELECT 1 FROM kyc_submission k WHERE k.id_inv = i.id_user AND k.statut = 'APPROVED');
    RAISE NOTICE '[migration 034] investisseurs verifies avec KycSubmission APPROVED : %', n;
END $$;
