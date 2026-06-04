package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.TypeBienRequest;
import com.fursa.fursa_backend.dto.TypeBienResponse;
import com.fursa.fursa_backend.service.TypeBienRefService;
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
@RequestMapping("/api/types-bien")
@RequiredArgsConstructor
@Tag(name = "Types de bien", description = "Types de bien immobilier (CRUD admin, lecture publique)")
public class TypeBienController {

    private final TypeBienRefService service;

    @Operation(summary = "Lister les types de bien actifs (public, pour le wizard)")
    @GetMapping
    public ResponseEntity<List<TypeBienResponse>> listActifs() {
        return ResponseEntity.ok(service.listerActifs());
    }

    @Operation(summary = "Lister tous les types de bien (admin, incluant inactifs)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<List<TypeBienResponse>> listAdmin() {
        return ResponseEntity.ok(service.listerTous());
    }

    @Operation(summary = "Creer un type de bien (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin")
    public ResponseEntity<TypeBienResponse> creer(@Valid @RequestBody TypeBienRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @Operation(summary = "Modifier un type de bien (admin). Le code n'est pas modifiable.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/{id}")
    public ResponseEntity<TypeBienResponse> modifier(
            @PathVariable Long id,
            @Valid @RequestBody TypeBienRequest req) {
        return ResponseEntity.ok(service.modifier(id, req));
    }

    @Operation(summary = "Desactiver un type de bien (admin). Plus visible au wizard mais conserve sur les biens existants.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/desactiver")
    public ResponseEntity<Void> desactiver(@PathVariable Long id) {
        service.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un type de bien (admin). Echoue si des biens le referencent encore.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
