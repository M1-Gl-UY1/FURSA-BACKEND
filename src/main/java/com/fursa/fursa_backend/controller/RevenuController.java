package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.RevenuRequest;
import com.fursa.fursa_backend.dto.RevenuResponse;
import com.fursa.fursa_backend.service.RevenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/revenus")
@RequiredArgsConstructor
@Tag(name = "Revenus", description = "CRUD des revenus d'exploitation (admin) avant distribution")
public class RevenuController {

    private final RevenuService revenuService;

    @Operation(summary = "Enregistrer un nouveau revenu (admin)",
            description = "Admin : declare un revenu percu pour une propriete. Pret a etre distribue via POST /api/distribution/{revenuId}.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Revenu cree"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<RevenuResponse> creer(@Valid @RequestBody RevenuRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(revenuService.creer(request));
    }

    @Operation(summary = "Lister tous les revenus")
    @GetMapping
    public ResponseEntity<List<RevenuResponse>> lister() {
        return ResponseEntity.ok(revenuService.lister());
    }

    @Operation(summary = "Revenus d'une propriete")
    @GetMapping("/propriete/{proprieteId}")
    public ResponseEntity<List<RevenuResponse>> listerParPropriete(@PathVariable Long proprieteId) {
        return ResponseEntity.ok(revenuService.listerParPropriete(proprieteId));
    }

    @Operation(summary = "Detail d'un revenu")
    @GetMapping("/{id}")
    public ResponseEntity<RevenuResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(revenuService.getById(id));
    }
}
