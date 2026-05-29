-- ============================================================================
-- Phase post-MVP (Hugh) : SourceRevenu = operateur partenaire
-- ============================================================================
-- On remplace les anciennes valeurs BAIL / AIRBNB par les partenaires officiels
-- PAJE_SQUARE / FUMBA_TOWN. FURSA tokenise principalement leurs biens.
--
-- CRITIQUE : sans cette migration, les biens existants avec source_revenu =
-- 'BAIL' ou 'AIRBNB' planteraient a la lecture (EnumType.STRING : valeur
-- inexistante dans l'enum Java) -> 500.
-- ============================================================================

-- Les anciennes valeurs basculent vers AUTRE (on ne peut pas deviner le partenaire).
UPDATE propriete
    SET source_revenu = 'AUTRE'
    WHERE source_revenu IN ('BAIL', 'AIRBNB');
