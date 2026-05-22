package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KycRejectRequest(
        @NotBlank @Size(min = 5, max = 500) String motif
) {}
