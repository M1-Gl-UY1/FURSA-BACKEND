package com.fursa.fursa_backend.payment.provider;

import com.fursa.fursa_backend.payment.PaymentProvider;
import com.fursa.fursa_backend.payment.ProviderSessionRequest;
import com.fursa.fursa_backend.payment.ProviderSessionResponse;
import com.fursa.fursa_backend.payment.WebhookEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Squelette de l'integration Yellow Card. A completer une fois FURSA dote d'une entite juridique
 * et que le KYB business Yellow Card sera valide (cf DESIGN_PAIEMENTS.md section 5.3).
 *
 * Tant que les vraies cles API ne sont pas dispos :
 *   - YELLOW_CARD_API_KEY et YELLOW_CARD_WEBHOOK_SECRET sont des chaines vides
 *   - createSession() leve UnsupportedOperationException
 *   - verifyWebhookSignature() retourne false
 *
 * Du coup ce bean est present pour permettre la compilation et l'injection, mais ne doit pas
 * etre selectionne comme provider actif tant que les cles ne sont pas configurees.
 */
@Component
public class YellowCardProvider implements PaymentProvider {

    public static final String NAME = "YELLOW_CARD";

    private final String apiKey;
    private final String webhookSecret;
    private final String apiBaseUrl;

    public YellowCardProvider(
            @Value("${yellow-card.api-key:}") String apiKey,
            @Value("${yellow-card.webhook-secret:}") String webhookSecret,
            @Value("${yellow-card.api-base-url:https://api.yellowcard.io/business}") String apiBaseUrl) {
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        this.apiBaseUrl = apiBaseUrl;
    }

    @Override
    public String getName() {
        return NAME;
    }

    /** Indique si les cles d'API sont configurees (sinon, provider non utilisable). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && webhookSecret != null && !webhookSecret.isBlank();
    }

    @Override
    public ProviderSessionResponse createSession(ProviderSessionRequest request) {
        if (!isConfigured()) {
            throw new UnsupportedOperationException(
                    "YellowCardProvider non configure : YELLOW_CARD_API_KEY et YELLOW_CARD_WEBHOOK_SECRET requis. "
                  + "A obtenir apres KYB business Yellow Card. Voir DESIGN_PAIEMENTS.md section 5.3.");
        }
        // TODO : appel POST {apiBaseUrl}/payments/sessions avec
        //   - header  : Authorization: Bearer {apiKey}
        //   - body    : { idempotencyKey, amount, currency, callbacks, metadata }
        //   - reponse : { sessionId, widgetUrl, expiresAt }
        throw new UnsupportedOperationException("YellowCardProvider.createSession a completer apres acces sandbox Yellow Card");
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (!isConfigured()) {
            return false;
        }
        // TODO : HMAC-SHA256(rawBody, webhookSecret) compare en temps constant avec {signature}.
        // L'algo et le header exact (X-Yellow-Card-Signature ?) seront fournis dans la doc Yellow Card apres KYB.
        return false;
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        // TODO : parser le JSON Yellow Card et le mapper sur WebhookEvent neutre.
        throw new UnsupportedOperationException("YellowCardProvider.parseWebhook a completer apres acces sandbox Yellow Card");
    }
}
