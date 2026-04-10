package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Investisseur extends User{

    private String nom;
    private String prenom;
    private String telephone;
    private Boolean isVerified;

    @Column(unique = true)
    private String wallet_address;

    // Table d'association "Recevoir" du MCD
    @ManyToMany
    @JoinTable(
            name = "recevoir",
            joinColumns = @JoinColumn(name = "id_inv"),
            inverseJoinColumns = @JoinColumn(name = "id_not")
    )
    private List<Notification> notifications;
}
