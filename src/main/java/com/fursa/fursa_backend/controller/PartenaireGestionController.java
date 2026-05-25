package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.PartenaireGestionRequest;
import com.fursa.fursa_backend.dto.PartenaireGestionResponse;
import com.fursa.fursa_backend.model.enumeration.TypePartenaire;
import com.fursa.fursa_backend.service.PartenaireGestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * P9 (Hugh 22/05/2026) : endpoints partenaires FURSA + assignation gestionnaire bien.
 */
@RestController
@RequestMapping("/api/partenaires-gestion")
@RequiredArgsConstructor
@Tag(name = "Partenaires", description = "Partenaires FURSA (gestion locative, promoteurs, blockchain, expertise)")
public class PartenaireGestionController {

    private final PartenaireGestionService service;

    @Operation(summary = "Liste publique des partenaires actifs",
            description = "Optionnellement filtrable par type via ?type=GESTION_LOCATIVE")
    @GetMapping
    public ResponseEntity<List<PartenaireGestionResponse>> lister(
            @RequestParam(required = false) TypePartenaire type) {
        if (type != null) {
            return ResponseEntity.ok(service.listerActifsParType(type));
        }
        return ResponseEntity.ok(service.listerActifs());
    }

    // ========================================================================
    // Admin
    // ========================================================================

    @Operation(summary = "Liste complete (admin)",
            description = "Inclut les partenaires desactives.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<List<PartenaireGestionResponse>> listerAdmin() {
        return ResponseEntity.ok(service.listerTous());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin")
    public ResponseEntity<PartenaireGestionResponse> creer(
            @Valid @RequestBody PartenaireGestionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/{id}")
    public ResponseEntity<PartenaireGestionResponse> modifier(
            @PathVariable Long id, @Valid @RequestBody PartenaireGestionRequest req) {
        return ResponseEntity.ok(service.modifier(id, req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Assigner / retirer un gestionnaire a une propriete (admin)",
            description = "Body : { partenaireId: number | null }. Seuls les partenaires GESTION_LOCATIVE actifs sont acceptes.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/proprietes/{proprieteId}/assigner")
    public ResponseEntity<Void> assigner(
            @PathVariable Long proprieteId,
            @RequestBody Map<String, Long> body) {
        Long partenaireId = body == null ? null : body.get("partenaireId");
        service.assignerGestionnaire(proprieteId, partenaireId);
        return ResponseEntity.noContent().build();
    }
}
