package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_not")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_investisseur", nullable = false)
    private Investisseur investisseur;

    private String titre;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private TypeMessage type;

    private LocalDateTime date;

    /**
     * false = non lu
     * true = lu
     */
    private Boolean statut = false;

    // ✅ Helper method to access User safely
    public User getUser() {
        return (investisseur != null) ? investisseur.getUser() : null;
    }
}