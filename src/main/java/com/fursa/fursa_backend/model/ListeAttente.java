package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutListeAttente;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P2 (Hugh 22/05/2026) : inscription d'un investisseur en liste d'attente
 * sur un bien finance.
 *
 * Le cumul des inscriptions EN_ATTENTE alimente le bonus_demande du mecanisme
 * de prix dynamique. Voir PRIX_DYNAMIQUE_FURSA.md §4.
 */
@Entity
@Table(name = "liste_attente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ListeAttente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prop", nullable = false)
    private Propriete propriete;

    /** ID de l'investisseur en attente (pas de FK pour rester souple). */
    @Column(name = "id_inv", nullable = false)
    private Long investisseurId;

    /** Nombre de parts souhaite (1-100). */
    @Column(name = "nombre_parts", nullable = false)
    private Integer nombreParts;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", length = 20, nullable = false)
    private StatutListeAttente statut = StatutListeAttente.EN_ATTENTE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "notifie_le")
    private LocalDateTime notifieLe;

    @Column(name = "servi_le")
    private LocalDateTime serviLe;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (statut == null) statut = StatutListeAttente.EN_ATTENTE;
    }
}
