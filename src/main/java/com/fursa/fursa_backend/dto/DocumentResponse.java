package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.CategorieDocument;
import com.fursa.fursa_backend.model.enumeration.SectionPhoto;
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
    /** P1 (Hugh 22/05/2026) : section structuree pour les photos. Null pour PDFs/contrats. */
    private SectionPhoto sectionPhoto;
    /**
     * V2 G.4 (05/06/2026) : code de section admin-configurable (peut etre
     * un code custom hors enum, ex TERRASSE). Source de verite frontend.
     */
    private String sectionPhotoCode;
    /** V2 G.4 : libelle resolu cote backend (ex "Façade avant", "Terrasse"). */
    private String sectionPhotoLabel;
    /** P8 (Hugh 22/05/2026) : categorie precise pour les documents legaux. */
    private CategorieDocument categorieDocument;
    /**
     * V2 G.2 (04/06/2026) : code de categorie admin-configurable (peut etre
     * un code custom hors enum). Source de verite pour le frontend.
     */
    private String categorieDocumentCode;
    /** V2 G.2 : libelle resolu cote backend (ex "Titre foncier", "Assurance habitation"). */
    private String categorieDocumentLabel;
}