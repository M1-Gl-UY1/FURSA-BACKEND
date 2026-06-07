package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutSyncBlockchain;
import com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * V2 R (07/06/2026) : job de synchronisation blockchain en attente / retry.
 *
 * Chaque appel a BlockchainSyncService / RevenueLedgerService qui echoue est
 * persiste ici. Un worker scheduled poll la table et retente, avec backoff
 * exponentiel (1min, 5min, 15min, 1h, 6h, abandon).
 *
 * Le payload est stocke en JSON (jsonb) — pas d'entite typee — pour que :
 *  - le format puisse evoluer sans migration de schema
 *  - on puisse re-executer un job meme si l'entite source a change entre temps
 */
@Entity
@Table(name = "blockchain_sync_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainSyncTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private TypeSyncBlockchain type;

    /** Reference fonctionnelle (propriete_id pour SYNC_PRIX/SET_STATUT, revenu_id pour les autres). */
    @Column(name = "ref_id")
    private Long refId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StatutSyncBlockchain status = StatutSyncBlockchain.PENDING;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "tx_hash", length = 80)
    private String txHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
