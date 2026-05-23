package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.MethodeRetrait;
import com.fursa.fursa_backend.model.enumeration.SourceRetrait;
import com.fursa.fursa_backend.model.enumeration.StatutDemandeRetrait;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10e : demande de retrait de fonds (wallet investisseur OU escrow proprio).
 *
 * Modele Hugh 22/05/2026 :
 * - Le proprio ne touche PAS directement le cash : il fait une demande qui doit
 *   etre validee par l'admin avant que les fonds quittent le compte FURSA.
 * - Commission FURSA (5%) prelevee a la validation.
 * - Pour les retraits cash (MOBILE_MONEY/VIREMENT/CRYPTO), l'admin execute le
 *   paiement manuellement hors-systeme puis marque COMPLETED.
 */
@Entity
@Table(name = "demande_retrait", indexes = {
        @Index(name = "idx_retrait_user", columnList = "id_user, created_at"),
        @Index(name = "idx_retrait_statut", columnList = "statut"),
        @Index(name = "idx_retrait_source", columnList = "source, id_source")
})
@Getter
@Setter
@NoArgsConstructor
public class DemandeRetrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_retrait")
    private Long id;

    /** User qui fait la demande. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_user", nullable = false)
    private Investisseur user;

    /** Source des fonds (WALLET ou ESCROW_PROPRIETE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 24)
    private SourceRetrait source;

    /**
     * ID de la source : wallet_id si source=WALLET, propriete_id si source=ESCROW_PROPRIETE.
     */
    @Column(name = "id_source", nullable = false)
    private Long sourceId;

    /** Montant demande (USD). */
    @Column(name = "montant_demande", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantDemande;

    /** Commission FURSA prelevee a la validation (USD). Initialement null/0. */
    @Column(name = "commission_fursa", precision = 15, scale = 2)
    private BigDecimal commissionFursa = BigDecimal.ZERO;

    /** Net effectivement verse au user (montant - commission). */
    @Column(name = "montant_final", precision = 15, scale = 2)
    private BigDecimal montantFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "methode", nullable = false, length = 24)
    private MethodeRetrait methode;

    /**
     * Reference cible pour le paiement (numero MM, IBAN, adresse crypto).
     * Null pour WALLET_INTERNE.
     */
    @Column(name = "reference_cible", length = 200)
    private String referenceCible;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 24)
    private StatutDemandeRetrait statut = StatutDemandeRetrait.PENDING;

    @Column(name = "motif_refus", length = 500)
    private String motifRefus;

    /** Preuve d'execution du paiement cash (reference virement, hash crypto...). */
    @Column(name = "preuve_paiement", length = 500)
    private String preuvePaiement;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "validee_le")
    private LocalDateTime valideeLe;

    @Column(name = "completee_le")
    private LocalDateTime completeeLe;

    /** Admin qui a valide / refuse. */
    @Column(name = "validee_par_admin_id")
    private Long valideeParAdminId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (statut == null) statut = StatutDemandeRetrait.PENDING;
        if (commissionFursa == null) commissionFursa = BigDecimal.ZERO;
    }
}
