package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Phase 10b : statut de declaration mensuel pour une propriete.
 *
 * Retourne pour chaque propriete proposee par un proprietaire :
 * - le mois a declarer (M-1 par rapport a aujourd'hui)
 * - si la declaration a deja ete soumise pour ce mois
 * - si on est encore dans la fenetre normale (1-5) ou si penalite s'applique
 *
 * Statuts :
 * - DECLARE : revenu deja soumis pour le mois N-1
 * - DANS_FENETRE : pas encore declare, mais on est dans les 1-5 (pas de penalite)
 * - EN_RETARD : pas encore declare, on est au-dela du 5 (penalite si declaration tardive)
 */
public record StatutDeclarationResponse(
        Long proprieteId,
        String proprieteNom,
        String moisADeclarer,      // "2026-05" (mois N-1)
        Statut statut,
        Integer joursRestants,     // nombre de jours avant fermeture de la fenetre (peut etre negatif si retard)
        Boolean dansFenetre,       // true si jour courant <= 5
        BigDecimal penaliteSiDeclarationMaintenant,
        LocalDate dateSoumission,  // null si pas encore declare
        Long revenuId              // id du revenu deja declare, null sinon
) {
    public enum Statut {
        DECLARE,
        DANS_FENETRE,
        EN_RETARD
    }
}
