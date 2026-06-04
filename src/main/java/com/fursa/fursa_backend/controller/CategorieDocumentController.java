package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.CategorieDocumentRequest;
import com.fursa.fursa_backend.dto.CategorieDocumentResponse;
import com.fursa.fursa_backend.service.CategorieDocumentRefService;
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
@RequestMapping("/api/categories-document")
@RequiredArgsConstructor
@Tag(name = "Categories de document", description = "Categories de document legal (CRUD admin, lecture publique)")
public class CategorieDocumentController {

    private final CategorieDocumentRefService service;

    @Operation(summary = "Lister les categories actives (public, pour le wizard)")
    @GetMapping
    public ResponseEntity<List<CategorieDocumentResponse>> listActifs() {
        return ResponseEntity.ok(service.listerActifs());
    }

    @Operation(summary = "Lister toutes les categories (admin, incluant inactives)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<List<CategorieDocumentResponse>> listAdmin() {
        return ResponseEntity.ok(service.listerTous());
    }

    @Operation(summary = "Creer une categorie de document (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin")
    public ResponseEntity<CategorieDocumentResponse> creer(
            @Valid @RequestBody CategorieDocumentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @Operation(summary = "Modifier une categorie (admin). Le code n'est pas modifiable.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/{id}")
    public ResponseEntity<CategorieDocumentResponse> modifier(
            @PathVariable Long id,
            @Valid @RequestBody CategorieDocumentRequest req) {
        return ResponseEntity.ok(service.modifier(id, req));
    }

    @Operation(summary = "Desactiver une categorie (admin). Plus visible au wizard mais conservee sur les documents existants.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/desactiver")
    public ResponseEntity<Void> desactiver(@PathVariable Long id) {
        service.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer une categorie (admin). Echoue si des documents la referencent encore.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
