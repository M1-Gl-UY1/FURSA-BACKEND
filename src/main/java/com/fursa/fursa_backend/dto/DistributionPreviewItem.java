package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;

/**
 * Preview d'un dividende a distribuer pour UN investisseur (sans persistance).
 * Calcule selon la strategie prorata : montantAttendu = montantTotal * parts / totalParts.
 */
public record DistributionPreviewItem(
        Long investisseurId,
        String email,
        String nom,
        String prenom,
        Integer nombreParts,
        Integer totalPartsPropriete,
        BigDecimal pourcentage,
        BigDecimal montantAttendu
) {}
