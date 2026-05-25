package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.TypePartenaire;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P9 (Hugh 22/05/2026) : partenaire FURSA.
 *
 * Couvre 4 roles : gestion locative (Airbnb, CPS), promoteur (Paje Square),
 * blockchain ops (SEED Innov), expertise locale (Africa Bahari).
 *
 * Un bien peut avoir un partenaire de gestion locative assigne (champ
 * gestionnaire_id sur Propriete) pour affichage "Gere par X" en fiche bien.
 */
@Entity
@Table(name = "partenaire_gestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartenaireGestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_partenaire", nullable = false, length = 30)
    private TypePartenaire typePartenaire;

    @Column(length = 500)
    private String description;

    @Column(name = "site_web", length = 300)
    private String siteWeb;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(nullable = false)
    private Boolean actif = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (actif == null) actif = true;
    }
}
