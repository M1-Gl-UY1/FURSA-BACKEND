package com.fursa.fursa_backend.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fursa.fursa_backend.payment.provider.MockPaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentProviderTest {

    private MockPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockPaymentProvider(new ObjectMapper(), "https://fursa.seed-innov.com");
    }

    @Test
    void getName_returns_MOCK() {
        assertThat(provider.getName()).isEqualTo("MOCK");
    }

    @Test
    void createSession_returns_externalId_prefixed_with_mock_and_widget_url_pointing_to_front() {
        ProviderSessionRequest req = new ProviderSessionRequest(
                "idem-1", "user@test.com", new BigDecimal("100"), "EUR",
                "https://front/success", "https://front/cancel", Map.of()
        );

        ProviderSessionResponse resp = provider.createSession(req);

        assertThat(resp.externalId()).startsWith("mock_");
        assertThat(resp.widgetUrl()).startsWith("https://fursa.seed-innov.com/mock-payment-widget?session=");
        assertThat(resp.widgetUrl()).endsWith(resp.externalId());
        assertThat(resp.expiresAt()).isAfter(java.time.LocalDateTime.now());
    }

    @Test
    void verifyWebhookSignature_always_returns_true_in_mock_mode() {
        assertThat(provider.verifyWebhookSignature("any", "fake-sig")).isTrue();
        assertThat(provider.verifyWebhookSignature("", null)).isTrue();
    }

    @Test
    void parseWebhook_maps_CONFIRMED_payload_to_PAYMENT_CONFIRMED_event() {
        String body = """
                {
                  "externalId": "mock_abc",
                  "status": "CONFIRMED",
                  "amount": "123.45",
                  "currency": "USDC",
                  "providerTxHash": "0xdeadbeef"
                }
                """;

        WebhookEvent event = provider.parseWebhook(body);

        assertThat(event.type()).isEqualTo(WebhookEventType.PAYMENT_CONFIRMED);
        assertThat(event.externalSessionId()).isEqualTo("mock_abc");
        assertThat(event.amountReceived()).isEqualByComparingTo("123.45");
        assertThat(event.currency()).isEqualTo("USDC");
        assertThat(event.providerTxHash()).isEqualTo("0xdeadbeef");
    }

    @Test
    void parseWebhook_maps_FAILED_payload_with_errorMessage() {
        String body = """
                {
                  "externalId": "mock_xyz",
                  "status": "FAILED",
                  "errorMessage": "Mobile Money debit refused"
                }
                """;

        WebhookEvent event = provider.parseWebhook(body);

        assertThat(event.type()).isEqualTo(WebhookEventType.PAYMENT_FAILED);
        assertThat(event.errorMessage()).isEqualTo("Mobile Money debit refused");
    }

    @Test
    void parseWebhook_unknown_status_maps_to_UNKNOWN_event() {
        String body = """
                { "externalId": "mock_a", "status": "PROCESSING" }
                """;

        WebhookEvent event = provider.parseWebhook(body);

        assertThat(event.type()).isEqualTo(WebhookEventType.UNKNOWN);
    }

    @Test
    void parseWebhook_invalid_json_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> provider.parseWebhook("not-a-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide");
    }
}
