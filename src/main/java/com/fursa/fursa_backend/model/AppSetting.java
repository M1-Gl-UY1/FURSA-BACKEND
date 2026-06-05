package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.TypeSetting;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V2 G.5 (05/06/2026) : parametre application admin-configurable.
 *
 * <p>Stocke en cle/valeur/type. La valeur est toujours stockee en string et
 * parsee par le service au moment de la lecture. Les services consommateurs
 * (FileStorageService, KycService, ...) appellent
 * {@code appSettingsService.getInt("file.max_size_pdf_mo", 10)} et ne se
 * soucient pas du parsing.
 */
@Entity
@Table(name = "app_setting")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AppSetting {

    @Id
    @Column(name = "cle", length = 100)
    private String cle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valeur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeSetting type = TypeSetting.STRING;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Groupe d'affichage cote admin : "Fichiers", "KYC", "Escrow", ... */
    @Column(nullable = false, length = 50)
    private String groupe = "AUTRE";

    /** Unite affichee a cote de la valeur dans la page admin (Mo, ans, %, etc.). */
    @Column(length = 20)
    private String unite;

    @Column(name = "ordre_affichage", nullable = false)
    private Integer ordre = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
