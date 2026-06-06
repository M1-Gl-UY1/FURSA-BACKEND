package com.fursa.fursa_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class PossessionResponse {
    /**
     * Bugfix 06/06/2026 : renommage pour aligner sur le contrat frontend
     * (le frontend lisait id/localisation/nombreDeParts depuis la nuit des
     * temps, le DTO exposait possessionId/proprieteLocalisation/nombreParts,
     * donc valeurs toujours undefined cote UI). Ajout de proprieteId pour
     * permettre au front de naviguer vers la fiche du bien sans cache hack.
     */
    private Long id;
    private Long proprieteId;
    private String proprieteNom;
    private String localisation;
    private Integer nombreDeParts;
    private BigDecimal prixUnitairePart;
    private BigDecimal valeurTotale;
    private Double rentabilitePrevue;
}
