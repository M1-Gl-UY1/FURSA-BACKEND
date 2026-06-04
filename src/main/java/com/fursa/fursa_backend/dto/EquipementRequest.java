package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EquipementRequest {

    /** En majuscules, sans espace. Ex "SALLE_DE_SPORT". */
    @NotBlank(message = "Le code est obligatoire")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
            message = "Code en majuscules, lettres/chiffres/underscore uniquement (ex SALLE_DE_SPORT)")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Le libelle est obligatoire")
    @Size(max = 100)
    private String label;

    @Size(max = 50)
    private String icone;

    private Integer ordre;

    private Boolean actif;
}
