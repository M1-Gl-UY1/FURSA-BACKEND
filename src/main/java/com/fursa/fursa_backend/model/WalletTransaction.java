package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.TypeWalletTransaction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10a : ligne du journal comptable d'un wallet (append-only).
 *
 * Cette table est IMMUABLE : aucune mise a jour, suppression interdite (audit financier).
 * Le signe du montant indique credit (+) ou debit (-). La somme de tous les montants
 * pour un wallet doit toujours egaler le solde courant du wallet (sanity check).
 *
 * Reference polymorphique : ref_table + ref_id pointent vers l'origine
 * (paiement, dividende, achat, demande_retrait, etc.) sans contrainte FK,
 * pour decouplage.
 */
@Entity
@Table(name = "wallet_transaction", indexes = {
        @Index(name = "idx_walletTx_wallet", columnList = "id_wallet, created_at"),
        @Index(name = "idx_walletTx_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_wtx")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_wallet", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private TypeWalletTransaction type;

    /**
     * Montant signe : > 0 = credit, < 0 = debit. En EUR.
     */
    @Column(name = "montant", nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    /**
     * Solde du wallet APRES application de cette transaction. Permet de tracer
     * l'historique sans recalcul, et detecter une eventuelle desynchronisation.
     */
    @Column(name = "solde_apres", nullable = false, precision = 15, scale = 2)
    private BigDecimal soldeApres;

    /** Table de reference (paiement, dividende, propriete, demande_retrait, ...). Optionnel. */
    @Column(name = "ref_table", length = 50)
    private String refTable;

    /** ID dans la table de reference. Optionnel. */
    @Column(name = "ref_id")
    private Long refId;

    /** Description humaine du mouvement (visible utilisateur). */
    @Column(name = "libelle", length = 255)
    private String libelle;

    /** Metadonnees JSON (provider PSP, hash transaction, motif admin, ...). Optionnel. */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
