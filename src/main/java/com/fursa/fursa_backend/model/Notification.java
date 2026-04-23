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

    private String titre;
    private String message;

    @Enumerated(EnumType.STRING)
    private TypeMessage type;
    private LocalDateTime date;

    private Boolean lu;

    @ManyToOne
    @JoinColumn(name = "id_inv")
    private Investisseur destinataire;
}
