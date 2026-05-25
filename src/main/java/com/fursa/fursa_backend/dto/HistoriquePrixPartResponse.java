package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.RaisonRecalculPrix;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot d'un changement de prix unitaire d'une part.
 * Voir PRIX_DYNAMIQUE_FURSA.md pour la specification complete du mecanisme.
 */
public record HistoriquePrixPartResponse(
        Long id,
        Long proprieteId,
        BigDecimal prixUnitaire,
        BigDecimal prixInitial,
        BigDecimal bonusRentabiliteTotal,
        BigDecimal bonusDemande,
        RaisonRecalculPrix raison,
        Long sourceId,
        BigDecimal variationPct,
        LocalDateTime createdAt
) {}
