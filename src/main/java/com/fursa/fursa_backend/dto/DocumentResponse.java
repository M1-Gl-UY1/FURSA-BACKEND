package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.TypeDocument;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentResponse {
    private Long id;
    private String nom;
    private String url;
    private TypeDocument type;
    private LocalDateTime dateUpload;
}