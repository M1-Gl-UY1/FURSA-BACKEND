package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V2 G.4 (05/06/2026) : sections photos admin-configurables.
 *
 * <p>Remplace progressivement l'enum {@code SectionPhoto} (qui reste expose
 * en lecture pour retro-compat). La source de verite est le code en string,
 * stocke dans {@code Document.sectionPhotoCode}.
 *
 * <p>L'admin peut creer des sections custom (TERRASSE, GARAGE, BALCON, CAVE,
 * BUREAU_INTERIEUR, ROOFTOP...) sans devoir redeployer.
 *
 * <p>Le nom de classe est SectionPhotoRef (et non SectionPhoto) pour eviter
 * la collision avec l'enum
 * {@code com.fursa.fursa_backend.model.enumeration.SectionPhoto}.
 */
@Entity
@Table(name = "section_photo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SectionPhotoRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_section_photo")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 50)
    private String icone;

    @Column(name = "ordre_affichage", nullable = false)
    private Integer ordre = 100;

    @Column(nullable = false)
    private Boolean actif = true;

    /**
     * Toujours requise dans le wizard. Pour V1, seules FACADE et SALON sont
     * a TRUE. Les regles conditionnelles (CHAMBRE si typeBien.exigeChambres,
     * PISCINE si equipement PISCINE, VUE si equipement VUE_MER) restent
     * hardcodees dans le wizard pour les codes historiques.
     */
    @Column(nullable = false)
    private Boolean requise = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
