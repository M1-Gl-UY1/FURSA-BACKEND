package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.RegleObligationDoc;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * V2 G.2 (04/06/2026) : payload de creation/modification d'une categorie
 * de document admin-configurable.
 */
@Getter
@Setter
public class CategorieDocumentRequest {

    /** En majuscules, sans espace. Ex "ASSURANCE_HABITATION". */
    @NotBlank(message = "Le code est obligatoire")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
            message = "Code en majuscules, lettres/chiffres/underscore uniquement")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Le libelle est obligatoire")
    @Size(max = 100)
    private String label;

    @Size(max = 500)
    private String description;

    @Size(max = 50)
    private String icone;

    private Integer ordre;

    private Boolean actif;

    /** Defaut OPTIONNEL si non fourni. */
    private RegleObligationDoc regleObligation;
}
