package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * V2 G.4 (05/06/2026) : payload de creation/modification d'une section
 * photo admin-configurable.
 */
@Getter
@Setter
public class SectionPhotoRequest {

    /** En majuscules, sans espace. Ex "TERRASSE". */
    @NotBlank(message = "Le code est obligatoire")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
            message = "Code en majuscules, lettres/chiffres/underscore uniquement")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Le libelle est obligatoire")
    @Size(max = 100)
    private String label;

    @Size(max = 50)
    private String icone;

    private Integer ordre;

    private Boolean actif;

    /** Si true, la section est obligatoire (au moins 1 photo) dans le wizard. */
    private Boolean requise;
}
