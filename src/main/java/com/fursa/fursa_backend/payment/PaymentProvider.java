package com.fursa.fursa_backend.payment;

import java.util.Optional;

/**
 * Abstraction d'un Payment Service Provider externe (Yellow Card, Stripe, Flutterwave, Mock...).
 *
 * Cycle de vie d'un paiement :
 *   1. createSession()   -> appel au PSP, retourne un externalId + widgetUrl
 *   2. (l'investisseur paie via le widget du PSP)
 *   3. webhook recu -> verifyWebhookSignature() + parseWebhook() -> WebhookEvent neutre
 *   4. (optionnel) querySessionStatus() pour les fallbacks (sessions PENDING orphelines)
 */
public interface PaymentProvider {

    /** Identifiant unique du provider (matche {@code PaymentSession.providerName}). */
    String getName();

    /** Cree une session de paiement chez le PSP. */
    ProviderSessionResponse createSession(ProviderSessionRequest request);

    /**
     * Verifie la signature HMAC d'un webhook entrant.
     * Algorithme et header varient selon le PSP. Doit utiliser une comparaison
     * constant-time pour eviter les timing attacks.
     */
    boolean verifyWebhookSignature(String rawBody, String signature);

    /** Parse le payload brut du webhook en evenement metier neutre. */
    WebhookEvent parseWebhook(String rawBody);

    /**
     * Optionnel : interroge le PSP pour connaitre l'etat d'une session sans attendre le webhook.
     * Utilise par un cron de fallback pour les sessions PENDING orphelines (webhook perdu).
     */
    default Optional<WebhookEvent> querySessionStatus(String externalId) {
        return Optional.empty();
    }
}
