package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutEscrow;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10c : compte sequestre par propriete.
 *
 * Quand un investisseur achete des parts sur une propriete EN_COLLECTE, l'argent
 * est debite de son wallet et credite ici (et PAS sur le wallet du proprietaire).
 *
 * - statut EN_COLLECTE : phase initiale, < 80% des parts vendues
 * - statut FINANCEE    : seuil atteint, les Possessions sont activees, le proprio peut
 *                        demander des retraits (Phase 10e) qui debiteront ce solde
 * - statut ANNULEE     : collecte annulee par admin, tout l'argent est rembourse aux
 *                        investisseurs
 *
 * Le solde represente l'argent disponible pour retrait par le proprietaire (ou refund
 * en cas d'annulation). Optimistic lock via @Version pour eviter les concurrences
 * d'achats simultanes.
 */
@Entity
@Table(name = "escrow_propriete", uniqueConstraints = {
        @UniqueConstraint(name = "uk_escrow_propriete", columnNames = "id_prop")
})
@Getter
@Setter
@NoArgsConstructor
public class EscrowPropriete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_escrow")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prop", nullable = false, unique = true)
    private Propriete propriete;

    /** Solde disponible (en USD) pour retrait par le proprietaire ou refund. */
    @Column(name = "solde", nullable = false, precision = 15, scale = 2)
    private BigDecimal solde = BigDecimal.ZERO;

    /**
     * Cumul historique des credits (achats investisseurs). Different du solde apres
     * retraits/commissions. Sert a calculer le pourcentage de collecte realise.
     */
    @Column(name = "total_collecte", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCollecte = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 16)
    private StatutEscrow statut = StatutEscrow.EN_COLLECTE;

    /**
     * Seuil de declenchement (pourcentage). Defaut 100% (reunion Hugh 22/05/2026 :
     * il faut que TOUTES les parts soient vendues avant que la propriete passe
     * a FINANCEE et que FURSA achete le bien chez le proprietaire reel).
     */
    @Column(name = "seuil_pct", nullable = false)
    private Integer seuilPct = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "financee_le")
    private LocalDateTime financeeLe;

    @Column(name = "annulee_le")
    private LocalDateTime annuleeLe;

    @Column(name = "motif_annulation", length = 500)
    private String motifAnnulation;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (solde == null) solde = BigDecimal.ZERO;
        if (totalCollecte == null) totalCollecte = BigDecimal.ZERO;
        if (statut == null) statut = StatutEscrow.EN_COLLECTE;
        if (seuilPct == null) seuilPct = 100;
    }
}
