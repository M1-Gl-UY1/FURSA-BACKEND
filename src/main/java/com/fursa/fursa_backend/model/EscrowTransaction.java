package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.TypeEscrowTransaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10c : ligne du journal d'un EscrowPropriete (append-only).
 *
 * Tracabilite complete des mouvements d'argent sur le compte sequestre d'une propriete.
 * Le signe du montant indique credit (+) ou debit (-) du solde escrow.
 *
 * Reference polymorphique vers l'origine du mouvement (ref_table + ref_id) :
 * - "wallet_transaction" -> tx wallet investisseur (achat)
 * - "demande_retrait"    -> retrait proprio (Phase 10e)
 * - etc.
 */
@Entity
@Table(name = "escrow_transaction", indexes = {
        @Index(name = "idx_escrowTx_escrow", columnList = "id_escrow, created_at"),
        @Index(name = "idx_escrowTx_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
public class EscrowTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_etx")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_escrow", nullable = false)
    private EscrowPropriete escrow;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private TypeEscrowTransaction type;

    /** Montant signe : > 0 = credit, < 0 = debit. En EUR. */
    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    /** Solde escrow APRES application de cette transaction. */
    @Column(name = "solde_apres", nullable = false, precision = 15, scale = 2)
    private BigDecimal soldeApres;

    /**
     * Pour les credits d'achat : reference au wallet investisseur (utile pour
     * tracer "qui a achete combien").
     */
    @Column(name = "id_inv")
    private Long investisseurId;

    @Column(name = "ref_table", length = 50)
    private String refTable;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "libelle", length = 255)
    private String libelle;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
