package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.SectionPhotoRequest;
import com.fursa.fursa_backend.dto.SectionPhotoResponse;
import com.fursa.fursa_backend.service.SectionPhotoRefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sections-photo")
@RequiredArgsConstructor
@Tag(name = "Sections photo", description = "Sections photo (CRUD admin, lecture publique)")
public class SectionPhotoController {

    private final SectionPhotoRefService service;

    @Operation(summary = "Lister les sections actives (public, pour le wizard)")
    @GetMapping
    public ResponseEntity<List<SectionPhotoResponse>> listActives() {
        return ResponseEntity.ok(service.listerActives());
    }

    @Operation(summary = "Lister toutes les sections (admin, incluant inactives)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<List<SectionPhotoResponse>> listAdmin() {
        return ResponseEntity.ok(service.listerToutes());
    }

    @Operation(summary = "Creer une section photo (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin")
    public ResponseEntity<SectionPhotoResponse> creer(
            @Valid @RequestBody SectionPhotoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @Operation(summary = "Modifier une section (admin). Le code n'est pas modifiable.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/{id}")
    public ResponseEntity<SectionPhotoResponse> modifier(
            @PathVariable Long id,
            @Valid @RequestBody SectionPhotoRequest req) {
        return ResponseEntity.ok(service.modifier(id, req));
    }

    @Operation(summary = "Desactiver une section (admin). Plus visible au wizard mais conservee sur les photos existantes.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/desactiver")
    public ResponseEntity<Void> desactiver(@PathVariable Long id) {
        service.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer une section (admin). Echoue si des photos la referencent encore.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
