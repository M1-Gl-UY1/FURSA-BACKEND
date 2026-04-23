package com.fursa.fursa_backend.controller;

import tools.jackson.databind.ObjectMapper;

import com.fursa.fursa_backend.config.JwtUtils;
import com.fursa.fursa_backend.config.SecurityConfig;
import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.dto.ProprieteResponse;
import com.fursa.fursa_backend.mapper.ProprieteMapper;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.service.CustomUserService;
import com.fursa.fursa_backend.service.ProprieteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProprieteController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class ProprieteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ProprieteService proprieteService;
    @MockitoBean private ProprieteMapper proprieteMapper;
    @MockitoBean private CustomUserService customUserService;
    @MockitoBean private JwtUtils jwtUtils;

    private Propriete propriete;
    private ProprieteResponse response;

    @BeforeEach
    void setUp() {
        propriete = new Propriete();
        propriete.setId(1L);
        propriete.setNom("Immeuble Bastos");
        propriete.setStatut(StatutPropriete.PUBLIEE);

        response = ProprieteResponse.builder()
                .id(1L)
                .nom("Immeuble Bastos")
                .localisation("Yaoundé")
                .nombreTotalPart(1000)
                .prixUnitairePart(new BigDecimal("50000"))
                .statut(StatutPropriete.PUBLIEE)
                .dateCreation(LocalDate.now())
                .documents(Collections.emptyList())
                .build();
    }

    // ── POST /admin ──────────────────────────────────────────────────────────

    @Test
    void ajouter_doitRetourner201() throws Exception {
        ProprieteRequest req = new ProprieteRequest();
        req.setNom("Immeuble Bastos");
        req.setLocalisation("Yaoundé");
        req.setNombreTotalPart(1000);
        req.setPrixUnitairePart(new BigDecimal("50000"));
        req.setStatut(StatutPropriete.PUBLIEE);

        when(proprieteService.creerPropriete(any(), any())).thenReturn(propriete);
        when(proprieteMapper.toResponse(propriete)).thenReturn(response);

        MockMultipartFile proprieteJson = new MockMultipartFile(
                "propriete", "", "application/json",
                objectMapper.writeValueAsBytes(req)
        );

        mockMvc.perform(multipart("/api/proprietes/admin")
                        .file(proprieteJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Immeuble Bastos"))
                .andExpect(jsonPath("$.statut").value("PUBLIEE"));
    }

    @Test
    void ajouter_avecFichier_doitPasser() throws Exception {
        ProprieteRequest req = new ProprieteRequest();
        req.setNom("Immeuble Bastos");
        req.setLocalisation("Yaoundé");
        req.setNombreTotalPart(1000);
        req.setPrixUnitairePart(new BigDecimal("50000"));
        req.setStatut(StatutPropriete.PUBLIEE);

        when(proprieteService.creerPropriete(any(), any())).thenReturn(propriete);
        when(proprieteMapper.toResponse(propriete)).thenReturn(response);

        MockMultipartFile proprieteJson = new MockMultipartFile(
                "propriete", "", "application/json",
                objectMapper.writeValueAsBytes(req)
        );
        MockMultipartFile fichier = new MockMultipartFile(
                "files", "facade.jpg", "image/jpeg", "image-content".getBytes()
        );

        mockMvc.perform(multipart("/api/proprietes/admin")
                        .file(proprieteJson)
                        .file(fichier))
                .andExpect(status().isCreated());
    }

    // ── GET /public ──────────────────────────────────────────────────────────

    @Test
    void lister_doitRetourner200AvecListe() throws Exception {
        when(proprieteService.listerTout()).thenReturn(List.of(propriete));
        when(proprieteMapper.toResponse(propriete)).thenReturn(response);

        mockMvc.perform(get("/api/proprietes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Immeuble Bastos"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void lister_aucunePropriete_doitRetournerListeVide() throws Exception {
        when(proprieteService.listerTout()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/proprietes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /public/{id} ─────────────────────────────────────────────────────

    @Test
    void getOne_idExistant_doitRetourner200() throws Exception {
        when(proprieteService.detail(1L)).thenReturn(propriete);
        when(proprieteMapper.toResponse(propriete)).thenReturn(response);

        mockMvc.perform(get("/api/proprietes/public/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nom").value("Immeuble Bastos"));
    }

    @Test
    void getOne_idInexistant_doitRetourner404() throws Exception {
        when(proprieteService.detail(99L))
                .thenThrow(new RuntimeException("Propriété introuvable : 99"));

        mockMvc.perform(get("/api/proprietes/public/99"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /admin/{id} ──────────────────────────────────────────────────────

    @Test
    void modifier_doitRetourner200() throws Exception {
        ProprieteRequest req = new ProprieteRequest();
        req.setNom("Immeuble Nlongkak");
        req.setLocalisation("Yaoundé");
        req.setNombreTotalPart(500);
        req.setPrixUnitairePart(new BigDecimal("30000"));
        req.setStatut(StatutPropriete.PUBLIEE);

        when(proprieteService.modifierPropriete(eq(1L), any(), any())).thenReturn(propriete);
        when(proprieteMapper.toResponse(propriete)).thenReturn(response);

        MockMultipartFile proprieteJson = new MockMultipartFile(
                "propriete", "", "application/json",
                objectMapper.writeValueAsBytes(req)
        );

        mockMvc.perform(multipart("/api/proprietes/admin/1")
                        .file(proprieteJson)
                        .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk());
    }

    // ── DELETE /admin/{id} ───────────────────────────────────────────────────

    @Test
    void supprimer_doitRetourner204() throws Exception {
        doNothing().when(proprieteService).supprimer(1L);

        mockMvc.perform(delete("/api/proprietes/admin/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void supprimer_idInexistant_doitRetourner404() throws Exception {
        doThrow(new RuntimeException("Propriété introuvable : 99"))
                .when(proprieteService).supprimer(99L);

        mockMvc.perform(delete("/api/proprietes/admin/99"))
                .andExpect(status().isNotFound());
    }
}