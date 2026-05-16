package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefusRevenuRequest(
        @NotBlank @Size(min = 10, max = 1000) String motif
) {}
