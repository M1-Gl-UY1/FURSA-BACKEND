package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * V2 G.3 (04/06/2026) : payload de creation/modification d'un type de bien
 * admin-configurable.
 */
@Getter
@Setter
public class TypeBienRequest {

    /** En majuscules, sans espace. Ex "LOFT", "MAISON_DE_VILLE". */
    @NotBlank(message = "Le code est obligatoire")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
            message = "Code en majuscules, lettres/chiffres/underscore uniquement (ex MAISON_DE_VILLE)")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Le libelle est obligatoire")
    @Size(max = 100)
    private String label;

    @Size(max = 50)
    private String icone;

    private Integer ordre;

    private Boolean actif;

    /** True par defaut : le wizard affichera le champ "Nb chambres". */
    private Boolean exigeChambres;
}
