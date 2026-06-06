package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.EquipementRequest;
import com.fursa.fursa_backend.dto.EquipementResponse;
import com.fursa.fursa_backend.model.Equipement;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.repository.EquipementRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * V2 H.6 (06/06/2026) : tests unitaires du service equipements
 * (CRUD basique + helper applyCodesToPropriete).
 */
class EquipementServiceTest {

    private EquipementRepository repository;
    private EquipementService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(EquipementRepository.class);
        service = new EquipementService(repository);
    }

    private static Equipement eq(Long id, String code, String label, boolean actif) {
        Equipement e = new Equipement();
        e.setId(id);
        e.setCode(code);
        e.setLabel(label);
        e.setOrdre(100);
        e.setActif(actif);
        return e;
    }

    @Test
    void creer_codeDuplique_throws() {
        when(repository.existsByCode("PISCINE")).thenReturn(true);
        EquipementRequest req = new EquipementRequest();
        req.setCode("PISCINE");
        req.setLabel("Piscine");

        assertThatThrownBy(() -> service.creer(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existe deja");
    }

    @Test
    void creer_nouveauCode_persisteAvecDefauts() {
        when(repository.existsByCode("LOFT")).thenReturn(false);
        when(repository.save(any(Equipement.class))).thenAnswer(inv -> inv.getArgument(0));
        EquipementRequest req = new EquipementRequest();
        req.setCode("LOFT");
        req.setLabel("Loft");

        EquipementResponse out = service.creer(req);

        assertThat(out.code()).isEqualTo("LOFT");
        assertThat(out.label()).isEqualTo("Loft");
        assertThat(out.ordre()).isEqualTo(100);
        assertThat(out.actif()).isTrue();
    }

    @Test
    void modifier_codeChange_throws() {
        when(repository.findById(1L)).thenReturn(Optional.of(eq(1L, "PISCINE", "Piscine", true)));
        EquipementRequest req = new EquipementRequest();
        req.setCode("AUTRE_CODE");
        req.setLabel("Nouveau");

        assertThatThrownBy(() -> service.modifier(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pas modifiable");
    }

    @Test
    void modifier_inexistant_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        EquipementRequest req = new EquipementRequest();
        req.setCode("X");
        req.setLabel("Y");

        assertThatThrownBy(() -> service.modifier(99L, req))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void applyCodesToPropriete_codesConnus_setBooleansEtSet() {
        Equipement piscine = eq(1L, "PISCINE", "Piscine", true);
        Equipement parking = eq(2L, "PARKING", "Parking", true);
        when(repository.findByCode("PISCINE")).thenReturn(Optional.of(piscine));
        when(repository.findByCode("PARKING")).thenReturn(Optional.of(parking));

        Propriete p = new Propriete();
        p.setEquipements(new HashSet<>());
        service.applyCodesToPropriete(p, List.of("PISCINE", "PARKING"));

        assertThat(p.getHasPiscine()).isTrue();
        assertThat(p.getHasParking()).isTrue();
        assertThat(p.getHasClimatisation()).isFalse();
        assertThat(p.getEquipements()).hasSize(2);
    }

    @Test
    void applyCodesToPropriete_listeVide_clearBooleansEtSet() {
        Propriete p = new Propriete();
        p.setHasPiscine(true);
        p.setHasParking(true);
        p.setEquipements(new HashSet<>());
        p.getEquipements().add(eq(1L, "PISCINE", "Piscine", true));

        service.applyCodesToPropriete(p, List.of());

        assertThat(p.getHasPiscine()).isFalse();
        assertThat(p.getHasParking()).isFalse();
        assertThat(p.getEquipements()).isEmpty();
    }

    @Test
    void applyCodesToPropriete_codeInconnu_ignoreSansException() {
        when(repository.findByCode("CODE_INVENTE")).thenReturn(Optional.empty());
        Propriete p = new Propriete();
        p.setEquipements(new HashSet<>());

        // Ne throw pas : le code custom est juste logge en warning.
        service.applyCodesToPropriete(p, List.of("CODE_INVENTE"));

        assertThat(p.getEquipements()).isEmpty();
    }

    @Test
    void applyCodesToPropriete_null_noop() {
        Propriete p = new Propriete();
        p.setHasPiscine(true);

        service.applyCodesToPropriete(p, null);

        // Aucun changement
        assertThat(p.getHasPiscine()).isTrue();
    }
}
