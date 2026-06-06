package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.AppSettingResponse;
import com.fursa.fursa_backend.dto.AppSettingUpdateRequest;
import com.fursa.fursa_backend.model.AppSetting;
import com.fursa.fursa_backend.model.enumeration.TypeSetting;
import com.fursa.fursa_backend.repository.AppSettingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * V2 H.6 (06/06/2026) : tests unitaires AppSettingsService
 * (CRUD admin + lectures typees avec cache + fallback).
 */
class AppSettingsServiceTest {

    private AppSettingRepository repository;
    private AppSettingsService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AppSettingRepository.class);
        service = new AppSettingsService(repository);
    }

    private static AppSetting setting(String cle, String valeur, TypeSetting type, String groupe) {
        AppSetting s = new AppSetting();
        s.setCle(cle);
        s.setValeur(valeur);
        s.setType(type);
        s.setLabel("test " + cle);
        s.setGroupe(groupe);
        s.setOrdre(100);
        return s;
    }

    // ========================================================================
    // Lectures typees
    // ========================================================================

    @Test
    void getInt_clePresenteValide() {
        when(repository.findById("kyc.age_minimum"))
                .thenReturn(Optional.of(setting("kyc.age_minimum", "18", TypeSetting.INTEGER, "KYC")));

        assertThat(service.getInt("kyc.age_minimum", 99)).isEqualTo(18);
    }

    @Test
    void getInt_cleAbsente_retourneDefault() {
        when(repository.findById("inconnu")).thenReturn(Optional.empty());

        assertThat(service.getInt("inconnu", 42)).isEqualTo(42);
    }

    @Test
    void getInt_valeurNonParseable_retourneDefault() {
        when(repository.findById("buggy"))
                .thenReturn(Optional.of(setting("buggy", "pas-un-int", TypeSetting.INTEGER, "Misc")));

        assertThat(service.getInt("buggy", 7)).isEqualTo(7);
    }

    @Test
    void getLong_OK() {
        when(repository.findById("file.max"))
                .thenReturn(Optional.of(setting("file.max", "12345678", TypeSetting.LONG, "Fichiers")));

        assertThat(service.getLong("file.max", 0L)).isEqualTo(12345678L);
    }

    @Test
    void getDecimal_OK() {
        when(repository.findById("rate"))
                .thenReturn(Optional.of(setting("rate", "1.25", TypeSetting.DECIMAL, "Misc")));

        assertThat(service.getDecimal("rate", BigDecimal.ZERO))
                .isEqualByComparingTo("1.25");
    }

    @Test
    void getBoolean_trueOK() {
        when(repository.findById("feat"))
                .thenReturn(Optional.of(setting("feat", "true", TypeSetting.BOOLEAN, "Misc")));

        assertThat(service.getBoolean("feat", false)).isTrue();
    }

    @Test
    void getBoolean_caseInsensitiveOK() {
        when(repository.findById("feat"))
                .thenReturn(Optional.of(setting("feat", "TRUE", TypeSetting.BOOLEAN, "Misc")));

        assertThat(service.getBoolean("feat", false)).isTrue();
    }

    @Test
    void getString_OK() {
        when(repository.findById("name"))
                .thenReturn(Optional.of(setting("name", "FURSA", TypeSetting.STRING, "Misc")));

        assertThat(service.getString("name", "default")).isEqualTo("FURSA");
    }

    @Test
    void getString_absent_returnsDefault() {
        when(repository.findById("name")).thenReturn(Optional.empty());

        assertThat(service.getString("name", "fallback")).isEqualTo("fallback");
    }

    // ========================================================================
    // Cache : 2eme appel ne hit pas le repo
    // ========================================================================

    @Test
    void getInt_appelsSuivantsUtilisentLeCache() {
        when(repository.findById("cached"))
                .thenReturn(Optional.of(setting("cached", "5", TypeSetting.INTEGER, "Misc")));

        // 1er appel : touche le repo
        assertThat(service.getInt("cached", 0)).isEqualTo(5);
        // 2eme appel : devrait utiliser le cache
        assertThat(service.getInt("cached", 0)).isEqualTo(5);
        assertThat(service.getInt("cached", 0)).isEqualTo(5);

        // Verifie qu'on a appele findById une seule fois
        Mockito.verify(repository, Mockito.times(1)).findById("cached");
    }

    // ========================================================================
    // CRUD admin
    // ========================================================================

    @Test
    void modifier_cleInconnue_throws() {
        when(repository.findById("inconnu")).thenReturn(Optional.empty());
        AppSettingUpdateRequest req = new AppSettingUpdateRequest();
        req.setValeur("18");

        assertThatThrownBy(() -> service.modifier("inconnu", req))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void modifier_typeIntegerValeurInvalide_throws() {
        when(repository.findById("kyc.age_minimum"))
                .thenReturn(Optional.of(setting("kyc.age_minimum", "18", TypeSetting.INTEGER, "KYC")));
        AppSettingUpdateRequest req = new AppSettingUpdateRequest();
        req.setValeur("pas-un-int");

        assertThatThrownBy(() -> service.modifier("kyc.age_minimum", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INTEGER");
    }

    @Test
    void modifier_typeBooleanValeurInvalide_throws() {
        when(repository.findById("feat"))
                .thenReturn(Optional.of(setting("feat", "true", TypeSetting.BOOLEAN, "Misc")));
        AppSettingUpdateRequest req = new AppSettingUpdateRequest();
        req.setValeur("yes-please");

        assertThatThrownBy(() -> service.modifier("feat", req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modifier_typeIntegerValeurValide_persiste() {
        AppSetting s = setting("kyc.age_minimum", "18", TypeSetting.INTEGER, "KYC");
        when(repository.findById("kyc.age_minimum")).thenReturn(Optional.of(s));
        when(repository.save(any(AppSetting.class))).thenAnswer(inv -> inv.getArgument(0));
        AppSettingUpdateRequest req = new AppSettingUpdateRequest();
        req.setValeur("21");

        AppSettingResponse out = service.modifier("kyc.age_minimum", req);

        assertThat(out.valeur()).isEqualTo("21");
    }

    @Test
    void listerPublics_filtreWhitelist() {
        when(repository.findAllByOrderByGroupeAscOrdreAsc()).thenReturn(List.of(
                setting("file.max_size_pdf_mo", "10", TypeSetting.INTEGER, "Fichiers"),
                setting("kyc.age_minimum", "18", TypeSetting.INTEGER, "KYC"),
                setting("commission.fursa_pct", "2.5", TypeSetting.DECIMAL, "Interne"),  // NOT public
                setting("file.max_size_image_mo", "4", TypeSetting.INTEGER, "Fichiers")
        ));

        List<AppSettingResponse> publics = service.listerPublics();

        assertThat(publics).extracting(AppSettingResponse::cle)
                .containsExactlyInAnyOrder(
                        "file.max_size_pdf_mo",
                        "file.max_size_image_mo",
                        "kyc.age_minimum");
        // Le setting interne ne doit PAS etre expose
        assertThat(publics).extracting(AppSettingResponse::cle)
                .doesNotContain("commission.fursa_pct");
    }
}
