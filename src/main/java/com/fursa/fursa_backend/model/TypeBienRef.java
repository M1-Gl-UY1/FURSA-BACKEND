package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V2 G.3 (04/06/2026) : types de bien admin-configurables.
 *
 * <p>Remplace progressivement l'enum {@code TypeBien} (qui reste expose en
 * lecture pour retro-compat). La source de verite est desormais le code en
 * string, stocke dans {@code Propriete.typeBienCode}.
 *
 * <p>L'admin peut creer des types custom (LOFT, MAISON_DE_VILLE, TERRAIN,
 * BUREAU, COMMERCE...) sans devoir redeployer.
 *
 * <p>Le champ {@code exigeChambres} pilote l'affichage du champ "Nombre de
 * chambres" dans le wizard : false pour STUDIO/CHAMBRE (et toute typologie
 * sans chambres distinctes definie ulterieurement).
 *
 * <p>Le nom de classe est TypeBienRef (et non TypeBien) pour eviter la
 * collision avec l'enum existant {@code com.fursa.fursa_backend.model.enumeration.TypeBien}.
 */
@Entity
@Table(name = "type_bien")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TypeBienRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_type_bien")
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

    @Column(name = "exige_chambres", nullable = false)
    private Boolean exigeChambres = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
