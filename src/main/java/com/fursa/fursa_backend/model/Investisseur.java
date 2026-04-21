// Investisseur.java - Version corrigée
package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Investisseur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inv")
    private Long id;

    private String nom;
    private String prenom;
    private String telephone;
    private String email;  // Ajouter email directement ici

    @OneToOne
    @JoinColumn(name = "id_user")
    private User user;
}