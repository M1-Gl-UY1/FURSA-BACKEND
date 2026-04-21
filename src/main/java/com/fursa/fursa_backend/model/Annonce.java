// Annonce.java - Version corrigée
package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Annonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal prixUnitaireDemande;

    @Enumerated(EnumType.STRING)
    private StatutAnnonce statut;

    private Integer nombreDePartsAVendre;

    @ManyToOne
    @JoinColumn(name = "id_inv")
    private Investisseur investisseur;

    @ManyToOne
    @JoinColumn(name = "id_prop")
    private Propriete propriete;

    @Column(name = "prix_total")
    private Double prixTotal;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @Column(name = "date_expiration")
    private LocalDateTime dateExpiration;

    // Méthode utilitaire pour vérifier si l'annonce est active
    public boolean isActive() {
        return StatutAnnonce.OUVERTE.equals(this.statut)
                && this.nombreDePartsAVendre > 0
                && (this.dateExpiration == null || this.dateExpiration.isAfter(LocalDateTime.now()));
    }
}