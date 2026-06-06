package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Document;
import com.fursa.fursa_backend.model.SectionPhotoRef;
import com.fursa.fursa_backend.model.enumeration.SectionPhoto;
import com.fursa.fursa_backend.repository.SectionPhotoRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V2 H.6 (06/06/2026) : tests unitaires du service sections photos
 * (helper applyToDocument avec sync enum + code custom).
 */
class SectionPhotoRefServiceTest {

    private SectionPhotoRefRepository repository;
    private SectionPhotoRefService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SectionPhotoRefRepository.class);
        service = new SectionPhotoRefService(repository);
    }

    private static SectionPhotoRef ref(String code, String label, boolean requise) {
        SectionPhotoRef s = new SectionPhotoRef();
        s.setCode(code);
        s.setLabel(label);
        s.setActif(true);
        s.setRequise(requise);
        s.setOrdre(100);
        return s;
    }

    @Test
    void applyToDocument_codeConnu_sync2cotes() {
        Document d = new Document();
        service.applyToDocument(d, "FACADE");

        assertThat(d.getSectionPhotoCode()).isEqualTo("FACADE");
        assertThat(d.getSectionPhoto()).isEqualTo(SectionPhoto.FACADE);
    }

    @Test
    void applyToDocument_codeCustomHorsEnum_fallbackAutre() {
        Document d = new Document();
        service.applyToDocument(d, "TERRASSE");

        assertThat(d.getSectionPhotoCode()).isEqualTo("TERRASSE");
        assertThat(d.getSectionPhoto()).isEqualTo(SectionPhoto.AUTRE);
    }

    @Test
    void applyToDocument_null_clearBoth() {
        Document d = new Document();
        d.setSectionPhoto(SectionPhoto.FACADE);
        d.setSectionPhotoCode("FACADE");

        service.applyToDocument(d, null);

        assertThat(d.getSectionPhotoCode()).isNull();
        assertThat(d.getSectionPhoto()).isNull();
    }

    @Test
    void applyToDocument_blank_clearBoth() {
        Document d = new Document();
        service.applyToDocument(d, "   ");

        assertThat(d.getSectionPhotoCode()).isNull();
        assertThat(d.getSectionPhoto()).isNull();
    }

    @Test
    void resoudreLabel_codeConnu_retourneLabel() {
        Mockito.when(repository.findByCode("FACADE"))
                .thenReturn(Optional.of(ref("FACADE", "Façade avant", true)));

        assertThat(service.resoudreLabel("FACADE")).isEqualTo("Façade avant");
    }

    @Test
    void resoudreLabel_inconnu_retourneCodeBrut() {
        Mockito.when(repository.findByCode("INCONNU")).thenReturn(Optional.empty());

        assertThat(service.resoudreLabel("INCONNU")).isEqualTo("INCONNU");
    }

    @Test
    void resoudreLabel_null_retourneNull() {
        assertThat(service.resoudreLabel(null)).isNull();
    }
}
