package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.mapper.ProprieteMapper;
import com.fursa.fursa_backend.model.Document;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.repository.DocumentRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProprieteServiceTest {

    @Mock private ProprieteRepository proprieteRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ProprieteMapper proprieteMapper;

    @InjectMocks private ProprieteService proprieteService;

    private ProprieteRequest request;
    private Propriete propriete;

    @BeforeEach
    void setUp() {
        request = new ProprieteRequest();
        request.setNom("Immeuble Bastos");
        request.setLocalisation("Yaoundé");
        request.setNombreTotalPart(1000);
        request.setPrixUnitairePart(new BigDecimal("50000"));
        request.setStatut(StatutPropriete.PUBLIEE);

        propriete = new Propriete();
        propriete.setId(1L);
        propriete.setNom("Immeuble Bastos");
        propriete.setLocalisation("Yaoundé");
        propriete.setNombreTotalPart(1000);
        propriete.setPrixUnitairePart(new BigDecimal("50000"));
        propriete.setStatut(StatutPropriete.PUBLIEE);
        propriete.setDateCreation(LocalDate.now());
    }

    // ── Création ────────────────────────────────────────────────────────────

    @Test
    void creerPropriete_sansFile_doitSauvegarderEtRetourner() {
        when(proprieteMapper.toEntity(request)).thenReturn(propriete);
        when(proprieteRepository.save(any())).thenReturn(propriete);
        when(proprieteRepository.findById(1L)).thenReturn(Optional.of(propriete));

        Propriete result = proprieteService.creerPropriete(request, null);

        assertThat(result).isNotNull();
        assertThat(result.getNom()).isEqualTo("Immeuble Bastos");
        verify(proprieteRepository, times(1)).save(any());
        verify(fileStorageService, never()).save(any()); // aucun fichier traité
    }

    @Test
    void creerPropriete_avecFichiers_doitSauvegarderDocuments() {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("facade.jpg");
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(fileStorageService.save(mockFile)).thenReturn("uuid_facade.jpg");

        when(proprieteMapper.toEntity(request)).thenReturn(propriete);
        when(proprieteRepository.save(any())).thenReturn(propriete);
        when(proprieteRepository.findById(1L)).thenReturn(Optional.of(propriete));

        Propriete result = proprieteService.creerPropriete(request, List.of(mockFile));

        assertThat(result).isNotNull();
        verify(fileStorageService, times(1)).save(mockFile);
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    void creerPropriete_avecPdf_doitDetecterTypePdf() {
        MultipartFile mockPdf = mock(MultipartFile.class);
        when(mockPdf.getOriginalFilename()).thenReturn("titre_foncier.pdf");
        when(mockPdf.getContentType()).thenReturn("application/pdf");
        when(fileStorageService.save(mockPdf)).thenReturn("uuid_titre.pdf");

        when(proprieteMapper.toEntity(request)).thenReturn(propriete);
        when(proprieteRepository.save(any())).thenReturn(propriete);
        when(proprieteRepository.findById(1L)).thenReturn(Optional.of(propriete));

        proprieteService.creerPropriete(request, List.of(mockPdf));

        // Vérifie que le document sauvegardé a le bon type PDF
        verify(documentRepository).save(argThat(doc ->
                doc.getType().name().equals("PDF")
        ));
    }

    // ── Lecture ─────────────────────────────────────────────────────────────

    @Test
    void listerTout_doitRetournerListe() {
        when(proprieteRepository.findAll()).thenReturn(List.of(propriete));

        List<Propriete> result = proprieteService.listerTout();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNom()).isEqualTo("Immeuble Bastos");
    }

    @Test
    void detail_idExistant_doitRetournerPropriete() {
        when(proprieteRepository.findById(1L)).thenReturn(Optional.of(propriete));

        Propriete result = proprieteService.detail(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void detail_idInexistant_doitLeverException() {
        when(proprieteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proprieteService.detail(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // ── Modification ─────────────────────────────────────────────────────────

    @Test
    void modifierPropriete_doitMettreAJourLesChamps() {
        request.setNom("Immeuble Nlongkak");
        when(proprieteRepository.findById(1L)).thenReturn(Optional.of(propriete));
        when(proprieteRepository.save(any())).thenReturn(propriete);

        Propriete result = proprieteService.modifierPropriete(1L, request, null);

        assertThat(result.getNom()).isEqualTo("Immeuble Nlongkak");
        verify(proprieteRepository).save(propriete);
    }

    @Test
    void modifierPropriete_idInexistant_doitLeverException() {
        when(proprieteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proprieteService.modifierPropriete(99L, request, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // ── Suppression ──────────────────────────────────────────────────────────

    @Test
    void supprimer_idExistant_doitSupprimerEtFichiers() {
        propriete.setDocuments(List.of()); // pas de documents
        when(proprieteRepository.findById(1L)).thenReturn(Optional.of(propriete));

        proprieteService.supprimer(1L);

        verify(proprieteRepository).deleteById(1L);
    }

    @Test
    void supprimer_avecDocuments_doitSupprimerFichiers() {
        Document doc = new Document();
        doc.setUrl("uuid_facade.jpg");
        propriete.setDocuments(List.of(doc));

        when(proprieteRepository.findById(1L)).thenReturn(Optional.of(propriete));

        proprieteService.supprimer(1L);

        verify(fileStorageService).delete("uuid_facade.jpg");
        verify(proprieteRepository).deleteById(1L);
    }

    @Test
    void supprimer_idInexistant_doitLeverException() {
        when(proprieteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proprieteService.supprimer(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }
}