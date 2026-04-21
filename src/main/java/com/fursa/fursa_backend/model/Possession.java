// Possession.java - Version corrigée
package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Possession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pos")
    private Long id;

    private Integer nombreDeParts;

    @ManyToOne
    @JoinColumn(name = "id_prop")
    private Propriete propriete;

    @ManyToOne
    @JoinColumn(name = "id_inv")  // Correction: "id inv" -> "id_inv"
    private Investisseur investisseur;

    @Column(name = "prix_achat")
    private Double prixAchat;

    @Column(name = "date_achat")
    private LocalDateTime dateAchat;
}