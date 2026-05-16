package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefusRequest {

    @NotBlank(message = "Le motif du refus est obligatoire")
    @Size(min = 10, max = 1000, message = "Le motif doit faire entre 10 et 1000 caractères")
    private String motif;
}
