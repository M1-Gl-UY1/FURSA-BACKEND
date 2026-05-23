package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.SectionPhoto;
import com.fursa.fursa_backend.model.enumeration.TypeDocument;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_doc")
    private Long id;

    private String nom;

    @Enumerated(EnumType.STRING)
    private TypeDocument type;

    private String url;
    private LocalDateTime dateUpload;

    @ManyToOne
    @JoinColumn(name = "id_prop")
    private Propriete propriete;

    /**
     * P1 (reunion Hugh 22/05/2026) : section structuree d'une photo.
     * Non-null pour les photos (FACADE, SALON, etc.), null pour les documents
     * techniques (PDFs legaux, contrats, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "section_photo", length = 20)
    private SectionPhoto sectionPhoto;
}
