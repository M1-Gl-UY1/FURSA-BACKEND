package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@Data
public abstract class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user")
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String motDePasse;

    @Column(name="adresse_portefeuille")
    private String adresse_portefeuille;

    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role {
        INVESTISSEUR, PROPRIETAIRE, ADMINISTRATEUR
    }



}
