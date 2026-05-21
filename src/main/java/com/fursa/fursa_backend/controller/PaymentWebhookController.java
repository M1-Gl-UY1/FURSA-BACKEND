package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.payment.PaymentProvider;
import com.fursa.fursa_backend.payment.PaymentProviderRegistry;
import com.fursa.fursa_backend.payment.WebhookEvent;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints d'entree pour les webhooks PSP.
 * <p>
 * Securite : aucune auth JWT, l'authenticite est verifiee par signature HMAC (delegue au provider).
 * SecurityConfig doit whitelister {@code /api/webhooks/**}.
 * <p>
 * Idempotence : {@link MarchePrimaireService#confirmerAchat} no-op si la session est deja CONFIRMED.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks paiement", description = "Endpoints appeles par les PSP (Yellow Card, Mock...)")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentProviderRegistry providerRegistry;
    private final MarchePrimaireService marchePrimaireService;

    @Operation(
            summary = "Webhook generique PSP",
            description = """
                    Recoit les notifications de paiement des PSP. Chaque PSP a son propre format
                    et son propre header de signature (HMAC). Le path variable {provider} discrimine.

                    Reponses :
                    - 200 OK : webhook traite (ou idempotent no-op si deja CONFIRMED)
                    - 401 Unauthorized : signature invalide
                    - 400 Bad Request : payload mal forme ou session introuvable
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Webhook traite"),
            @ApiResponse(responseCode = "401", description = "Signature HMAC invalide"),
            @ApiResponse(responseCode = "400", description = "Payload invalide ou session introuvable")
    })
    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String provider,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawBody) {

        PaymentProvider paymentProvider;
        try {
            paymentProvider = providerRegistry.getByName(provider);
        } catch (IllegalArgumentException e) {
            log.warn("Webhook recu pour provider inconnu : {}", provider);
            return ResponseEntity.badRequest().body(Map.of("error", "unknown_provider", "provider", provider));
        }

        if (!paymentProvider.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Signature webhook invalide pour provider {}", provider);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_signature"));
        }

        WebhookEvent event;
        try {
            event = paymentProvider.parseWebhook(rawBody);
        } catch (Exception e) {
            log.error("Webhook {} : payload invalide : {}", provider, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_payload", "message", e.getMessage()));
        }

        if (event.externalSessionId() == null || event.externalSessionId().isBlank()) {
            log.warn("Webhook {} sans externalSessionId", provider);
            return ResponseEntity.badRequest().body(Map.of("error", "missing_external_session_id"));
        }

        try {
            marchePrimaireService.confirmerAchat(event.externalSessionId(), event);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.warn("Webhook {} : session introuvable {}", provider, event.externalSessionId());
            return ResponseEntity.badRequest().body(Map.of("error", "session_not_found"));
        } catch (Exception e) {
            log.error("Webhook {} : echec confirmerAchat pour session {} : {}",
                    provider, event.externalSessionId(), e.getMessage(), e);
            // On laisse remonter en 500 -> le PSP retry tout seul (politique standard webhook)
            throw e;
        }

        return ResponseEntity.ok(Map.of(
                "status", "processed",
                "externalSessionId", event.externalSessionId(),
                "eventType", event.type().name()
        ));
    }
}
