package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AppSettingResponse;
import com.fursa.fursa_backend.dto.AppSettingUpdateRequest;
import com.fursa.fursa_backend.service.AppSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * V2 G.5 (05/06/2026) : controller admin pour configurer les settings
 * application (limites fichiers, age KYC, fenetre declaration, etc.).
 *
 * <p>Les seeds sont fournis par la migration 029. L'admin ne peut PAS creer
 * ou supprimer des cles, uniquement modifier la valeur.
 */
@RestController
@RequestMapping("/api/app-settings")
@RequiredArgsConstructor
@Tag(name = "App settings", description = "Parametres globaux configurables par l'admin")
public class AppSettingsController {

    private final AppSettingsService service;

    @Operation(summary = "Lister tous les settings (admin uniquement)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<List<AppSettingResponse>> listAdmin() {
        return ResponseEntity.ok(service.listerTous());
    }

    @Operation(summary = "Lister les settings publics (whitelist : limites fichiers, age KYC)")
    @GetMapping("/public")
    public ResponseEntity<List<AppSettingResponse>> listPublic() {
        return ResponseEntity.ok(service.listerPublics());
    }

    @Operation(summary = "Modifier la valeur d'un setting (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/{cle}")
    public ResponseEntity<AppSettingResponse> modifier(
            @PathVariable String cle,
            @Valid @RequestBody AppSettingUpdateRequest req) {
        return ResponseEntity.ok(service.modifier(cle, req));
    }
}
