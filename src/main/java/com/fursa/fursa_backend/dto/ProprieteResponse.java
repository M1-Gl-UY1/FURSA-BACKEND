package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProprieteResponse {
    private Long id;
    private String nom;
    private String localisation;
    private String description;
    private Integer nombreTotalPart;
    private Integer partsDisponibles;
    private BigDecimal prixUnitairePart;
    private StatutPropriete statut;
    private Double rentabilitePrevue;
    private LocalDate dateCreation;
    private List<DocumentResponse> documents;

    // --- Phase 7 : workflow soumission propriétaire ---
    private Long proposeurId;
    private String motifRefus;
    private LocalDateTime soumiseLe;
}
