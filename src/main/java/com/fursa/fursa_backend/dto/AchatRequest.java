package com.fursa.fursa_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AchatRequest {
    private Long investisseurId;
    private Long proprieteId;
    private Integer nombreParts;
}
