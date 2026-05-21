package com.fursa.fursa_backend.payment.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fursa.fursa_backend.payment.PaymentProvider;
import com.fursa.fursa_backend.payment.ProviderSessionRequest;
import com.fursa.fursa_backend.payment.ProviderSessionResponse;
import com.fursa.fursa_backend.payment.WebhookEvent;
import com.fursa.fursa_backend.payment.WebhookEventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Provider de test : auto-confirme via MockPaymentScheduler en profil dev.
 * En profil prod il est tout de meme bean (au cas ou) mais ne devrait pas etre selectionne
 * comme provider actif (cf futur PaymentProviderResolver).
 *
 * Format webhook simule par le scheduler :
 * <pre>
 * { "externalId": "mock_xxx", "status": "CONFIRMED", "amount": "123.45", "currency": "USDC" }
 * </pre>
 */
@Component
public class MockPaymentProvider implements PaymentProvider {

    public static final String NAME = "MOCK";

    private final ObjectMapper objectMapper;
    private final String frontBaseUrl;

    public MockPaymentProvider(
            ObjectMapper objectMapper,
            @Value("${app.front-base-url:https://fursa.seed-innov.com}") String frontBaseUrl) {
        this.objectMapper = objectMapper;
        this.frontBaseUrl = frontBaseUrl;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ProviderSessionResponse createSession(ProviderSessionRequest request) {
        String externalId = "mock_" + UUID.randomUUID();
        String widgetUrl = frontBaseUrl + "/mock-payment-widget?session=" + externalId;
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
        return new ProviderSessionResponse(externalId, widgetUrl, expiresAt);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        // Mode mock : pas de signature reelle, on accepte tout.
        return true;
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            String externalId = node.path("externalId").asText(null);
            String status = node.path("status").asText("UNKNOWN");
            BigDecimal amount = node.has("amount")
                    ? new BigDecimal(node.path("amount").asText("0"))
                    : BigDecimal.ZERO;
            String currency = node.path("currency").asText("USDC");
            String providerTxHash = node.path("providerTxHash").asText(null);
            String errorMessage = node.path("errorMessage").asText(null);

            WebhookEventType type = switch (status) {
                case "CONFIRMED" -> WebhookEventType.PAYMENT_CONFIRMED;
                case "FAILED" -> WebhookEventType.PAYMENT_FAILED;
                case "EXPIRED" -> WebhookEventType.PAYMENT_EXPIRED;
                default -> WebhookEventType.UNKNOWN;
            };
            return new WebhookEvent(type, externalId, amount, currency, providerTxHash, errorMessage);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Webhook MOCK invalide : " + e.getOriginalMessage(), e);
        }
    }
}
