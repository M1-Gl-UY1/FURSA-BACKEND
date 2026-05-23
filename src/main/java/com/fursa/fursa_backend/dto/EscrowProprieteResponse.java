package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.StatutEscrow;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10c : etat de collecte d'une propriete (crowdfunding).
 *
 * Affichage : barre de progression "X% collecte / Y% requis pour debloquer".
 */
public record EscrowProprieteResponse(
        Long id,
        Long proprieteId,
        String proprieteNom,
        BigDecimal solde,
        BigDecimal totalCollecte,
        BigDecimal montantCible,       // total parts × prix unitaire
        BigDecimal montantSeuil,       // montant cible × seuilPct / 100
        Double pourcentageCollecte,    // 0-100, du montantCible
        Integer seuilPct,
        StatutEscrow statut,
        LocalDateTime createdAt,
        LocalDateTime financeeLe,
        LocalDateTime annuleeLe,
        String motifAnnulation
) {}
