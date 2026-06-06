-- Migration V2 H.5 (06/06/2026) : renomme le groupe "KYC" en
-- "Verification d'identite" pour rester coherent avec le renommage UX
-- (le terme technique "KYC" reste dans les cles et noms de classes, mais
-- pas dans le texte affiche a l'admin).
--
-- Idempotent : si le groupe a deja ete renomme, l'UPDATE ne fait rien.

BEGIN;

UPDATE app_setting
SET groupe = 'Verification d''identite'
WHERE groupe = 'KYC';

-- Met aussi a jour les descriptions qui mentionnaient "KYC" en majuscules
-- visibles (le mot "verification" est suffisant pour l'admin).
UPDATE app_setting
SET description = REPLACE(description, 'le KYC est refuse', 'la verification est refusee')
WHERE cle = 'kyc.age_maximum'
  AND description LIKE '%le KYC est refuse%';

COMMIT;
