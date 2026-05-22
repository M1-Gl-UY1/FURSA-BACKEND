package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phase 10a : wallet polymorphique (1 par user, investisseur ou proprietaire).
 *
 * Source de verite fonctionnelle du solde. Mirror on-chain optionnel (Phase 10f via fEUR).
 * Tous les mouvements passent par WalletService pour garantir append-only + optimistic lock.
 *
 * Le solde est toujours en EUR (devise figee pour MVP, multi-devise en V2).
 * Le solde ne peut jamais etre negatif (CHECK constraint + validation service).
 */
@Entity
@Table(name = "wallet", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_user", columnNames = "id_user")
})
@Getter
@Setter
@NoArgsConstructor
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_wallet")
    private Long id;

    /**
     * User proprietaire du wallet (investisseur ou proprietaire de bien).
     * Polymorphique car n'importe quel role peut avoir un wallet.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false, unique = true)
    private Investisseur user;

    /** Solde courant en EUR. Toujours >= 0. */
    @Column(name = "solde", nullable = false, precision = 15, scale = 2)
    private BigDecimal solde = BigDecimal.ZERO;

    /** Devise (toujours EUR en MVP, multi-devise en V2). */
    @Column(name = "devise", nullable = false, length = 3)
    private String devise = "EUR";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (solde == null) solde = BigDecimal.ZERO;
        if (devise == null) devise = "EUR";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
