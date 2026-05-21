package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.DeviseRate;
import com.fursa.fursa_backend.repository.DeviseRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class DeviseRateService {

    private static final int USDC_SCALE = 6;

    private final DeviseRateRepository repository;

    public DeviseRateService(DeviseRateRepository repository) {
        this.repository = repository;
    }

    /**
     * Convertit un montant en devise fiat vers son equivalent USDC.
     * Si la devise est inconnue, leve une IllegalArgumentException (caller doit la mapper en 400).
     */
    public BigDecimal convertirEnUsdc(BigDecimal montantFiat, String codeDevise) {
        if (montantFiat == null || codeDevise == null) {
            throw new IllegalArgumentException("montantFiat et codeDevise sont obligatoires");
        }
        if (montantFiat.signum() <= 0) {
            throw new IllegalArgumentException("montantFiat doit etre strictement positif");
        }
        String code = codeDevise.trim().toUpperCase();
        DeviseRate rate = repository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Devise non supportee : " + code));
        return montantFiat
                .multiply(rate.getTauxVersUsdc())
                .setScale(USDC_SCALE, RoundingMode.HALF_UP);
    }

    @Transactional
    public DeviseRate upsert(String codeDevise, BigDecimal tauxVersUsdc) {
        if (tauxVersUsdc == null || tauxVersUsdc.signum() <= 0) {
            throw new IllegalArgumentException("tauxVersUsdc doit etre strictement positif");
        }
        String code = codeDevise.trim().toUpperCase();
        DeviseRate rate = repository.findById(code).orElseGet(() -> {
            DeviseRate r = new DeviseRate();
            r.setCodeDevise(code);
            return r;
        });
        rate.setTauxVersUsdc(tauxVersUsdc);
        rate.setUpdatedAt(LocalDateTime.now());
        return repository.save(rate);
    }
}
