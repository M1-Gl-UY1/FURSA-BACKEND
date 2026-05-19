package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AchatRequest {

    @NotNull(message = "proprieteId est obligatoire")
    private Long proprieteId;

    @NotNull(message = "nombreParts est obligatoire")
    @Positive(message = "nombreParts doit etre strictement superieur a 0")
    private Integer nombreParts;
}
