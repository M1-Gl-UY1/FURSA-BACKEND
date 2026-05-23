package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.MethodeRetrait;
import com.fursa.fursa_backend.model.enumeration.SourceRetrait;
import com.fursa.fursa_backend.model.enumeration.StatutDemandeRetrait;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10e : representation publique d'une demande de retrait.
 */
public record DemandeRetraitResponse(
        Long id,
        Long userId,
        String userEmail,
        String userNomComplet,
        SourceRetrait source,
        Long sourceId,
        /** Nom de la propriete si source=ESCROW_PROPRIETE, null sinon. */
        String sourceLibelle,
        BigDecimal montantDemande,
        BigDecimal commissionFursa,
        BigDecimal montantFinal,
        MethodeRetrait methode,
        String referenceCible,
        StatutDemandeRetrait statut,
        String motifRefus,
        String preuvePaiement,
        LocalDateTime createdAt,
        LocalDateTime valideeLe,
        LocalDateTime completeeLe,
        Long valideeParAdminId
) {}
