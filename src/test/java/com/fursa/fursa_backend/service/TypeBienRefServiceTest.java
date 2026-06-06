package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.TypeBienRequest;
import com.fursa.fursa_backend.dto.TypeBienResponse;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.TypeBienRef;
import com.fursa.fursa_backend.model.enumeration.TypeBien;
import com.fursa.fursa_backend.repository.TypeBienRefRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * V2 H.6 (06/06/2026) : tests unitaires du service types de bien
 * (CRUD + helper applyToPropriete avec sync enum + code).
 */
class TypeBienRefServiceTest {

    private TypeBienRefRepository repository;
    private TypeBienRefService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TypeBienRefRepository.class);
        service = new TypeBienRefService(repository);
    }

    private static TypeBienRef ref(Long id, String code, String label) {
        TypeBienRef t = new TypeBienRef();
        t.setId(id);
        t.setCode(code);
        t.setLabel(label);
        t.setOrdre(100);
        t.setActif(true);
        t.setExigeChambres(true);
        return t;
    }

    @Test
    void creer_codeDuplique_throws() {
        when(repository.existsByCode("VILLA")).thenReturn(true);
        TypeBienRequest req = new TypeBienRequest();
        req.setCode("VILLA");
        req.setLabel("Villa");

        assertThatThrownBy(() -> service.creer(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creer_exigeChambresDefautTrue() {
        when(repository.existsByCode("LOFT")).thenReturn(false);
        when(repository.save(any(TypeBienRef.class))).thenAnswer(inv -> inv.getArgument(0));
        TypeBienRequest req = new TypeBienRequest();
        req.setCode("LOFT");
        req.setLabel("Loft");

        TypeBienResponse out = service.creer(req);

        assertThat(out.exigeChambres()).isTrue();
    }

    @Test
    void resoudreLabel_codeConnu_retourneLabel() {
        when(repository.findByCode("VILLA"))
                .thenReturn(Optional.of(ref(1L, "VILLA", "Villa")));

        assertThat(service.resoudreLabel("VILLA")).isEqualTo("Villa");
    }

    @Test
    void resoudreLabel_codeInconnu_retourneCodeBrut() {
        when(repository.findByCode("INCONNU")).thenReturn(Optional.empty());

        assertThat(service.resoudreLabel("INCONNU")).isEqualTo("INCONNU");
    }

    @Test
    void resoudreLabel_null_retourneNull() {
        assertThat(service.resoudreLabel(null)).isNull();
    }

    @Test
    void applyToPropriete_codeConnuDansEnum_sync2cotes() {
        Propriete p = new Propriete();
        service.applyToPropriete(p, "VILLA", null);

        assertThat(p.getTypeBienCode()).isEqualTo("VILLA");
        assertThat(p.getTypeBien()).isEqualTo(TypeBien.VILLA);
    }

    @Test
    void applyToPropriete_codeCustomHorsEnum_setCodeEnumNull() {
        Propriete p = new Propriete();
        p.setTypeBien(TypeBien.VILLA);  // valeur initiale

        service.applyToPropriete(p, "LOFT", null);

        assertThat(p.getTypeBienCode()).isEqualTo("LOFT");
        assertThat(p.getTypeBien()).isNull();
    }

    @Test
    void applyToPropriete_enumOnlyFallbackOk() {
        Propriete p = new Propriete();
        service.applyToPropriete(p, null, TypeBien.APPARTEMENT);

        assertThat(p.getTypeBienCode()).isEqualTo("APPARTEMENT");
        assertThat(p.getTypeBien()).isEqualTo(TypeBien.APPARTEMENT);
    }

    @Test
    void applyToPropriete_lesDeuxNull_noop() {
        Propriete p = new Propriete();
        p.setTypeBien(TypeBien.VILLA);
        p.setTypeBienCode("VILLA");

        service.applyToPropriete(p, null, null);

        // Aucun changement
        assertThat(p.getTypeBienCode()).isEqualTo("VILLA");
        assertThat(p.getTypeBien()).isEqualTo(TypeBien.VILLA);
    }

    @Test
    void modifier_inexistant_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        TypeBienRequest req = new TypeBienRequest();
        req.setCode("X");
        req.setLabel("Y");

        assertThatThrownBy(() -> service.modifier(99L, req))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
