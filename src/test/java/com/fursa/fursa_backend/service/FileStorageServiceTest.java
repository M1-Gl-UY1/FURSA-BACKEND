package com.fursa.fursa_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() throws Exception {
        fileStorageService = new FileStorageService();
        // Redirige le dossier "uploads" vers le dossier temporaire du test
        Field rootField = FileStorageService.class.getDeclaredField("root");
        rootField.setAccessible(true);
        rootField.set(fileStorageService, tempDir);
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    void save_doitRetournerNomFichierAvecExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "contenu".getBytes()
        );

        String nom = fileStorageService.save(file);

        assertThat(nom).endsWith(".jpg");
        assertThat(tempDir.resolve(nom)).exists();
    }

    @Test
    void save_nomAvecEspacesEtCaracteresSpeciaux_doitFonctionner() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "mon immeuble (2).pdf", "application/pdf", "pdf".getBytes()
        );

        String nom = fileStorageService.save(file);

        assertThat(nom).endsWith(".pdf");
        assertThat(tempDir.resolve(nom)).exists();
    }

    @Test
    void save_deuxFichiersIdentiques_doitCreerDeuxFichiersDistincts() {
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "contenu1".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "contenu2".getBytes()
        );

        String nom1 = fileStorageService.save(file1);
        String nom2 = fileStorageService.save(file2);

        // Les noms doivent être différents (UUID garantit l'unicité)
        assertThat(nom1).isNotEqualTo(nom2);
        assertThat(tempDir.resolve(nom1)).exists();
        assertThat(tempDir.resolve(nom2)).exists();
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    @Test
    void load_fichierExistant_doitRetournerResourceLisible() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "data".getBytes()
        );
        String nom = fileStorageService.save(file);

        Resource resource = fileStorageService.load(nom);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
    }

    @Test
    void load_fichierInexistant_doitLeverException() {
        assertThatThrownBy(() -> fileStorageService.load("inexistant.jpg"))
                .isInstanceOf(RuntimeException.class);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_fichierExistant_doitSupprimerDuDisque() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a_supprimer.jpg", "image/jpeg", "data".getBytes()
        );
        String nom = fileStorageService.save(file);
        assertThat(tempDir.resolve(nom)).exists();

        fileStorageService.delete(nom);

        assertThat(tempDir.resolve(nom)).doesNotExist();
    }

    @Test
    void delete_fichierInexistant_doitNePasLeverException() {
        assertThatNoException()
                .isThrownBy(() -> fileStorageService.delete("fantome.jpg"));
    }
}