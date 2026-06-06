package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.StatutRevenu;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * V2 L (06/06/2026) : catalogue des periodes trimestrielles declarables pour
 * une propriete donnee.
 *
 * Le frontend appelle GET /api/revenus/propriete/{id}/periodes-trimestres et
 * recoit la liste des trimestres (Q4 N-1 + Q1..Q4 N), chacun annote avec son
 * statut metier (deja declare / disponible / a venir).
 */
public record PeriodeTrimestrielleResponse(
        /** Code court "2026-Q1". */
        String code,
        /** Libelle long "1er trimestre 2026 (jan-fev-mar)". */
        String libelle,
        LocalDate dateDebut,
        LocalDate dateFin,
        Statut statut,
        /** Renseigne si statut == DEJA_DECLARE (le revenu actif non-REFUSE). */
        Long revenuId,
        StatutRevenu statutRevenu,
        BigDecimal montantDeclare,
        LocalDate dateDeclaration
) {
    public enum Statut {
        /** Trimestre terminé et aucun revenu actif (EN_REVIEW ou VALIDE) — déclaration possible. */
        DECLARABLE,
        /** Un revenu EN_REVIEW ou VALIDE existe déjà pour cette période. */
        DEJA_DECLARE,
        /** Trimestre pas encore terminé (dateFin > today). */
        A_VENIR
    }
}
