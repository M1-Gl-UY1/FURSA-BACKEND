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

    /**
     * P5 (Hugh 22/05/2026) : conversion devise locale -> USD pour l'affichage utilisateur.
     * Equivalent a convertirEnUsdc mais arrondi a 2 decimales (centimes USD).
     *
     * Retourne null si montant null ou <= 0. Retourne le montant inchange si la devise
     * est USD ou null. Leve IllegalArgumentException si la devise est inconnue.
     */
    public BigDecimal toUsd(BigDecimal montant, String codeDevise) {
        if (montant == null || montant.signum() <= 0) return null;
        if (codeDevise == null || codeDevise.trim().isEmpty()
                || "USD".equalsIgnoreCase(codeDevise.trim())) {
            return montant.setScale(2, RoundingMode.HALF_UP);
        }
        return convertirEnUsdc(montant, codeDevise)
                .setScale(2, RoundingMode.HALF_UP);
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
