package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.EquipementRequest;
import com.fursa.fursa_backend.dto.EquipementResponse;
import com.fursa.fursa_backend.service.EquipementService;
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
@RequestMapping("/api/equipements")
@RequiredArgsConstructor
@Tag(name = "Equipements", description = "Equipements de bien immobilier (CRUD admin, lecture publique)")
public class EquipementController {

    private final EquipementService service;

    @Operation(summary = "Lister les equipements actifs (public, pour le wizard)")
    @GetMapping
    public ResponseEntity<List<EquipementResponse>> listActifs() {
        return ResponseEntity.ok(service.listerActifs());
    }

    @Operation(summary = "Lister tous les equipements (admin, incluant inactifs)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<List<EquipementResponse>> listAdmin() {
        return ResponseEntity.ok(service.listerTous());
    }

    @Operation(summary = "Creer un equipement (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin")
    public ResponseEntity<EquipementResponse> creer(@Valid @RequestBody EquipementRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @Operation(summary = "Modifier un equipement (admin). Le code n'est pas modifiable.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/{id}")
    public ResponseEntity<EquipementResponse> modifier(
            @PathVariable Long id,
            @Valid @RequestBody EquipementRequest req) {
        return ResponseEntity.ok(service.modifier(id, req));
    }

    @Operation(summary = "Desactiver un equipement (admin). Plus visible au wizard mais conserve sur les biens.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/desactiver")
    public ResponseEntity<Void> desactiver(@PathVariable Long id) {
        service.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer un equipement (admin). Echoue si des biens le referencent encore.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
