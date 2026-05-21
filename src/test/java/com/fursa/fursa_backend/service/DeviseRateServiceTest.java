package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.DeviseRate;
import com.fursa.fursa_backend.repository.DeviseRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviseRateServiceTest {

    private DeviseRateRepository repository;
    private DeviseRateService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DeviseRateRepository.class);
        service = new DeviseRateService(repository);
    }

    private static DeviseRate rate(String code, String taux) {
        return new DeviseRate(code, new BigDecimal(taux), LocalDateTime.now());
    }

    @Test
    void convertirEnUsdc_EUR_to_USDC_at_1_10() {
        when(repository.findById("EUR")).thenReturn(Optional.of(rate("EUR", "1.10")));

        BigDecimal result = service.convertirEnUsdc(new BigDecimal("100"), "EUR");

        assertThat(result).isEqualByComparingTo("110.000000");
    }

    @Test
    void convertirEnUsdc_XAF_to_USDC_at_0_0017_with_correct_scale() {
        when(repository.findById("XAF")).thenReturn(Optional.of(rate("XAF", "0.0017")));

        BigDecimal result = service.convertirEnUsdc(new BigDecimal("50000"), "XAF");

        // 50000 * 0.0017 = 85.00 USDC, arrondi a 6 decimales
        assertThat(result).isEqualByComparingTo("85.000000");
        assertThat(result.scale()).isEqualTo(6);
    }

    @Test
    void convertirEnUsdc_lowercase_devise_is_normalized_to_uppercase() {
        when(repository.findById("USD")).thenReturn(Optional.of(rate("USD", "1.00")));

        BigDecimal result = service.convertirEnUsdc(new BigDecimal("42"), "usd");

        assertThat(result).isEqualByComparingTo("42.000000");
    }

    @Test
    void convertirEnUsdc_unknown_devise_throws_IllegalArgumentException() {
        when(repository.findById("BTC")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convertirEnUsdc(new BigDecimal("1"), "BTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BTC");
    }

    @Test
    void convertirEnUsdc_negative_amount_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> service.convertirEnUsdc(new BigDecimal("-1"), "EUR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positif");
    }

    @Test
    void convertirEnUsdc_zero_amount_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> service.convertirEnUsdc(BigDecimal.ZERO, "EUR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertirEnUsdc_null_amount_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> service.convertirEnUsdc(null, "EUR"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsert_new_devise_creates_and_persists() {
        when(repository.findById("THB")).thenReturn(Optional.empty());
        when(repository.save(any(DeviseRate.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviseRate saved = service.upsert("THB", new BigDecimal("0.028"));

        assertThat(saved.getCodeDevise()).isEqualTo("THB");
        assertThat(saved.getTauxVersUsdc()).isEqualByComparingTo("0.028");
        assertThat(saved.getUpdatedAt()).isNotNull();
        verify(repository).save(any(DeviseRate.class));
    }

    @Test
    void upsert_existing_devise_updates_taux_and_timestamp() {
        DeviseRate existing = rate("EUR", "1.05");
        LocalDateTime oldTs = existing.getUpdatedAt();
        when(repository.findById("EUR")).thenReturn(Optional.of(existing));
        when(repository.save(any(DeviseRate.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviseRate updated = service.upsert("EUR", new BigDecimal("1.12"));

        assertThat(updated.getTauxVersUsdc()).isEqualByComparingTo("1.12");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(oldTs);
    }

    @Test
    void upsert_negative_taux_rejected() {
        assertThatThrownBy(() -> service.upsert("EUR", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positif");
        verify(repository, never()).save(any());
        verify(repository, never()).findById(eq("EUR"));
    }
}
