package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.CategorieDocument;
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

    /**
     * V2 G.4 (05/06/2026) : code de section photo admin-configurable (source
     * de verite). Pour les 9 codes historiques, contient la meme valeur que
     * {@code sectionPhoto.name()}. Pour les codes custom crees par l'admin
     * (TERRASSE, GARAGE, BALCON, ...), {@code sectionPhoto} fallback sur
     * AUTRE et seul ce champ porte la valeur reelle.
     */
    @Column(name = "section_photo_code", length = 50)
    private String sectionPhotoCode;

    /**
     * P8 (Hugh 22/05/2026) : categorie precise d'un document legal
     * (TITRE_FONCIER, CONTRAT_BAIL, RELEVE_AIRBNB, etc.). Permet a l'admin
     * de voir le type de preuve fournie au lieu d'un PDF generique.
     * Null pour les photos (qui ont sectionPhoto a la place).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie_document", length = 30)
    private CategorieDocument categorieDocument;

    /**
     * V2 G.2 (04/06/2026) : code de categorie admin-configurable (source de
     * verite). Pour les 6 codes historiques, contient la meme valeur que
     * {@code categorieDocument.name()}. Pour les codes custom crees par
     * l'admin (ASSURANCE_HABITATION, DIAGNOSTIC_DPE, ...), {@code
     * categorieDocument} fallback sur AUTRE et seul ce champ porte la
     * valeur reelle.
     */
    @Column(name = "categorie_document_code", length = 50)
    private String categorieDocumentCode;
}
