package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.RaisonRecalculPrix;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot d'un changement de prix unitaire d'une part.
 *
 * P1 (Hugh 22/05/2026) : prix dynamique. Chaque variation du prix courant
 * est tracee ici pour permettre l'affichage de l'historique et calculer
 * la tendance (sparkline).
 */
@Entity
@Table(name = "historique_prix_part")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoriquePrixPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_prop", nullable = false)
    private Propriete propriete;

    /** Prix unitaire courant apres recalcul. */
    @Column(name = "prix_unitaire", precision = 15, scale = 2, nullable = false)
    private BigDecimal prixUnitaire;

    /** Snapshot du prix initial (pour reconstruire la formule). */
    @Column(name = "prix_initial", precision = 15, scale = 2, nullable = false)
    private BigDecimal prixInitial;

    /** Bonus cumule de rentabilite (fraction : 0.05 = +5%). */
    @Column(name = "bonus_rentabilite_total", precision = 8, scale = 6, nullable = false)
    private BigDecimal bonusRentabiliteTotal = BigDecimal.ZERO;

    /** Bonus instantane de demande (fraction : 0.10 = +10%). */
    @Column(name = "bonus_demande", precision = 8, scale = 6, nullable = false)
    private BigDecimal bonusDemande = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "raison", length = 40, nullable = false)
    private RaisonRecalculPrix raison;

    /** ID externe (revenu_id, transaction_id, etc.) selon la raison. Nullable. */
    @Column(name = "source_id")
    private Long sourceId;

    /** Variation par rapport au prix initial, en % (ex: +12.50 = +12.5%). */
    @Column(name = "variation_pct", precision = 8, scale = 4, nullable = false)
    private BigDecimal variationPct = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
