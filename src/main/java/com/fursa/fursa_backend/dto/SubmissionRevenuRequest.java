package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Soumission d'une déclaration de revenu par un propriétaire (Phase 8).
 * Le statut est forcé à EN_REVIEW côté serveur.
 * Vérification : la propriété doit appartenir au proposeur.
 */
public record SubmissionRevenuRequest(
        @NotNull Long proprieteId,
        @NotNull @Positive BigDecimal montantTotal,
        LocalDate periodeDebut,
        LocalDate periodeFin
) {}
