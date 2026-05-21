package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutPaymentSession;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_session",
        indexes = {
                @Index(name = "idx_payment_session_external_id", columnList = "external_id", unique = true),
                @Index(name = "idx_payment_session_expires_at", columnList = "expires_at"),
                @Index(name = "idx_payment_session_statut_provider", columnList = "statut, provider_name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 100, unique = true)
    private String externalId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inv", nullable = false)
    private Investisseur investisseur;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prop", nullable = false)
    private Propriete propriete;

    @Column(name = "nombre_parts", nullable = false)
    private Integer nombreParts;

    @Column(name = "montant_fiat", nullable = false, precision = 19, scale = 2)
    private BigDecimal montantFiat;

    @Column(name = "devise_fiat", nullable = false, length = 3)
    private String deviseFiat;

    @Column(name = "montant_usdc", nullable = false, precision = 38, scale = 18)
    private BigDecimal montantUsdc;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    @Column(name = "widget_url", columnDefinition = "TEXT")
    private String widgetUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutPaymentSession statut;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "paiement_id")
    private Long paiementId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "possession_id")
    private Long possessionId;

    @Column(name = "webhook_raw_payload", columnDefinition = "TEXT")
    private String webhookRawPayload;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Version
    private Long version;
}
