package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.AppSettingResponse;
import com.fursa.fursa_backend.dto.AppSettingUpdateRequest;
import com.fursa.fursa_backend.model.AppSetting;
import com.fursa.fursa_backend.model.enumeration.TypeSetting;
import com.fursa.fursa_backend.repository.AppSettingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2 G.5 (05/06/2026) : service de gestion des settings application.
 *
 * <p>Expose deux usages :
 * <ul>
 *   <li><b>Lecture par les services consommateurs</b> via les helpers typees
 *       {@link #getInt(String, int)}, {@link #getLong(String, long)},
 *       {@link #getDecimal(String, BigDecimal)}, {@link #getBoolean(String, boolean)},
 *       {@link #getString(String, String)}. Toujours fournir une valeur par
 *       defaut : tant que la migration 029 n'est pas appliquee ou qu'une cle
 *       est manquante, la valeur par defaut hardcodee s'applique (zero
 *       regression).</li>
 *   <li><b>CRUD admin</b> via {@link #listerTous()} et {@link #modifier(String, AppSettingUpdateRequest)}.</li>
 * </ul>
 *
 * <p>Un cache local ConcurrentHashMap evite un round-trip BDD a chaque
 * {@code getInt(...)} dans la hot path (upload de fichier par exemple).
 * Le cache est invalide a chaque PUT admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingsService {

    private final AppSettingRepository repository;
    private final Map<String, AppSetting> cache = new ConcurrentHashMap<>();

    // ========================================================================
    // CRUD admin
    // ========================================================================

    public List<AppSettingResponse> listerTous() {
        return repository.findAllByOrderByGroupeAscOrdreAsc().stream()
                .map(this::toResponse).toList();
    }

    /**
     * V2 H.4 (06/06/2026) : settings exposes a l'investisseur (lecture publique).
     * Whitelist stricte : uniquement les valeurs UI-impact que le wizard
     * frontend a besoin de connaitre dynamiquement (limites tailles fichiers,
     * age KYC). PAS de secrets ni de parametres internes (commission, seuils
     * blockchain, etc.).
     */
    private static final java.util.Set<String> PUBLIC_KEYS = java.util.Set.of(
            "file.max_size_pdf_mo",
            "file.max_size_image_mo",
            "file.max_size_video_mo",
            "kyc.age_minimum",
            "kyc.age_maximum"
    );

    public List<AppSettingResponse> listerPublics() {
        return repository.findAllByOrderByGroupeAscOrdreAsc().stream()
                .filter(s -> PUBLIC_KEYS.contains(s.getCle()))
                .map(this::toResponse).toList();
    }

    @Transactional
    public AppSettingResponse modifier(String cle, AppSettingUpdateRequest req) {
        AppSetting s = repository.findById(cle)
                .orElseThrow(() -> new EntityNotFoundException("Setting introuvable : " + cle));
        // Validation que la valeur est parseable dans le type declare.
        validerParsing(req.getValeur(), s.getType());
        s.setValeur(req.getValeur());
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
        cache.put(cle, s);
        log.info("AppSetting modifie : cle={} valeur={}", cle, req.getValeur());
        return toResponse(s);
    }

    private void validerParsing(String valeur, TypeSetting type) {
        try {
            switch (type) {
                case INTEGER -> Integer.parseInt(valeur);
                case LONG    -> Long.parseLong(valeur);
                case DECIMAL -> new BigDecimal(valeur);
                case BOOLEAN -> {
                    String v = valeur.trim().toLowerCase();
                    if (!v.equals("true") && !v.equals("false")) {
                        throw new IllegalArgumentException("Booleen attendu (true/false)");
                    }
                }
                case STRING -> { /* tout est valide */ }
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                "La valeur '" + valeur + "' n'est pas parseable en " + type);
        }
    }

    // ========================================================================
    // Lecture typee (pour les services consommateurs)
    // ========================================================================

    /**
     * Lit la valeur INTEGER pour la cle. Renvoie {@code defaultValue} si la
     * cle n'existe pas ou si la valeur est unparseable.
     */
    public int getInt(String cle, int defaultValue) {
        AppSetting s = load(cle);
        if (s == null) return defaultValue;
        try {
            return Integer.parseInt(s.getValeur().trim());
        } catch (NumberFormatException ex) {
            log.warn("Setting {} : valeur '{}' invalide pour INTEGER, defaut {}",
                    cle, s.getValeur(), defaultValue);
            return defaultValue;
        }
    }

    /** Lit la valeur LONG pour la cle. */
    public long getLong(String cle, long defaultValue) {
        AppSetting s = load(cle);
        if (s == null) return defaultValue;
        try {
            return Long.parseLong(s.getValeur().trim());
        } catch (NumberFormatException ex) {
            log.warn("Setting {} : valeur '{}' invalide pour LONG, defaut {}",
                    cle, s.getValeur(), defaultValue);
            return defaultValue;
        }
    }

    /** Lit la valeur DECIMAL pour la cle. */
    public BigDecimal getDecimal(String cle, BigDecimal defaultValue) {
        AppSetting s = load(cle);
        if (s == null) return defaultValue;
        try {
            return new BigDecimal(s.getValeur().trim());
        } catch (NumberFormatException ex) {
            log.warn("Setting {} : valeur '{}' invalide pour DECIMAL, defaut {}",
                    cle, s.getValeur(), defaultValue);
            return defaultValue;
        }
    }

    /** Lit la valeur BOOLEAN pour la cle. */
    public boolean getBoolean(String cle, boolean defaultValue) {
        AppSetting s = load(cle);
        if (s == null) return defaultValue;
        String v = s.getValeur().trim().toLowerCase();
        if (v.equals("true")) return true;
        if (v.equals("false")) return false;
        log.warn("Setting {} : valeur '{}' invalide pour BOOLEAN, defaut {}",
                cle, s.getValeur(), defaultValue);
        return defaultValue;
    }

    /** Lit la valeur STRING pour la cle. */
    public String getString(String cle, String defaultValue) {
        AppSetting s = load(cle);
        return s != null ? s.getValeur() : defaultValue;
    }

    /** Lookup avec cache. Renvoie null si la cle n'existe pas. */
    private AppSetting load(String cle) {
        AppSetting cached = cache.get(cle);
        if (cached != null) return cached;
        AppSetting fromDb = repository.findById(cle).orElse(null);
        if (fromDb != null) cache.put(cle, fromDb);
        return fromDb;
    }

    private AppSettingResponse toResponse(AppSetting s) {
        return new AppSettingResponse(
                s.getCle(), s.getValeur(), s.getType(),
                s.getLabel(), s.getDescription(),
                s.getGroupe(), s.getUnite(), s.getOrdre());
    }
}
