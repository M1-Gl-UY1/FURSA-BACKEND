package com.fursa.fursa_backend.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resoud le PaymentProvider actif par nom (config {@code app.payment.active-provider})
 * et fournit aussi l'acces par nom pour les webhooks ({@code /api/webhooks/{provider}}).
 *
 * Configuration :
 * <pre>
 * app:
 *   payment:
 *     active-provider: MOCK   # dev: MOCK, prod: YELLOW_CARD apres KYB
 * </pre>
 */
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> byName;
    private final String activeProviderName;

    public PaymentProviderRegistry(
            List<PaymentProvider> providers,
            @Value("${app.payment.active-provider:MOCK}") String activeProviderName) {
        this.byName = providers.stream()
                .collect(Collectors.toMap(PaymentProvider::getName, p -> p));
        this.activeProviderName = activeProviderName.toUpperCase();
        if (!byName.containsKey(this.activeProviderName)) {
            throw new IllegalStateException(
                    "Active payment provider '" + activeProviderName + "' not found. "
                  + "Available: " + byName.keySet());
        }
    }

    /** Provider actif pour les nouveaux paiements. */
    public PaymentProvider getActive() {
        return byName.get(activeProviderName);
    }

    /** Recherche par nom pour les webhooks (chaque PSP a son endpoint dedie). */
    public PaymentProvider getByName(String name) {
        PaymentProvider provider = byName.get(name.toUpperCase());
        if (provider == null) {
            throw new IllegalArgumentException("Unknown payment provider: " + name);
        }
        return provider;
    }
}
