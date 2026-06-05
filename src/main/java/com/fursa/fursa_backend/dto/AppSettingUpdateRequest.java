package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * V2 G.5 (05/06/2026) : payload de mise a jour d'un setting (admin).
 *
 * <p>L'admin ne peut PAS creer ou supprimer des cles (elles sont definies par
 * les seeds des migrations). Il ne peut que modifier la valeur d'une cle
 * existante. Le service valide que la valeur est parseable dans le type.
 */
@Getter
@Setter
public class AppSettingUpdateRequest {

    @NotBlank(message = "La valeur est obligatoire")
    private String valeur;
}
