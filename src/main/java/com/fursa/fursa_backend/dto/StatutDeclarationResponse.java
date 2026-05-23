package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Phase 10b + P3 (Hugh 22/05/2026) : statut de declaration TRIMESTRIELLE
 * pour une propriete.
 *
 * Retourne pour chaque propriete proposee par un proprietaire :
 * - le trimestre a declarer (Q-1 par rapport a aujourd'hui)
 * - si la declaration a deja ete soumise pour ce trimestre
 * - si on est encore dans la fenetre normale (jours 1-15 du 1er mois du
 *   trimestre N+1) ou si penalite s'applique
 *
 * Statuts :
 * - DECLARE : revenu deja soumis pour le trimestre Q-1
 * - DANS_FENETRE : pas encore declare, mais on est dans les 1-15 d'un mois
 *   d'ouverture (janvier, avril, juillet, octobre) -> pas de penalite
 * - EN_RETARD : pas encore declare, on est au-dela du 15 ou hors mois d'ouverture
 *   (penalite si declaration tardive)
 */
public record StatutDeclarationResponse(
        Long proprieteId,
        String proprieteNom,
        /** Format "2026-Q1", "2026-Q2", ... — trimestre N-1 a declarer. */
        String moisADeclarer,      // nom conserve pour retro-compat frontend
        Statut statut,
        Integer joursRestants,
        Boolean dansFenetre,
        BigDecimal penaliteSiDeclarationMaintenant,
        LocalDate dateSoumission,
        Long revenuId
) {
    public enum Statut {
        DECLARE,
        DANS_FENETRE,
        EN_RETARD
    }
}
