package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.RefusRevenuRequest;
import com.fursa.fursa_backend.dto.RevenuRequest;
import com.fursa.fursa_backend.dto.RevenuResponse;
import com.fursa.fursa_backend.dto.SubmissionRevenuRequest;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/revenus")
@RequiredArgsConstructor
@Tag(name = "Revenus", description = "CRUD des revenus d'exploitation (admin) + workflow déclaration propriétaire")
public class RevenuController {

    private final RevenuService revenuService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    // =========================================================================
    // Workflow historique (admin)
    // =========================================================================

    @Operation(summary = "Enregistrer un nouveau revenu (admin)",
            description = "Admin : declare un revenu percu pour une propriete. Statut = VALIDE direct (peut etre distribue immediatement).")
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

    // =========================================================================
    // PHASE 8 : workflow déclaration propriétaire
    // =========================================================================

    @Operation(summary = "Soumettre une déclaration de revenu (propriétaire)",
            description = "Le propriétaire d'un bien soumet une déclaration de revenu en attente de validation admin. Statut auto = EN_REVIEW. Vérifie que le bien appartient bien à l'utilisateur.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Soumission enregistrée"),
            @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas propriétaire du bien"),
            @ApiResponse(responseCode = "404", description = "Propriété introuvable")
    })
    @PostMapping("/submissions")
    public ResponseEntity<RevenuResponse> soumettre(@Valid @RequestBody SubmissionRevenuRequest request) {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.status(HttpStatus.CREATED).body(revenuService.soumettre(userId, request));
    }

    @Operation(summary = "Mes déclarations de revenu", description = "Liste les revenus que j'ai déclarés sur mes biens.")
    @GetMapping("/me")
    public ResponseEntity<List<RevenuResponse>> mesRevenus() {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.ok(revenuService.listerMesRevenus(userId));
    }

    @Operation(summary = "Approuver une déclaration de revenu (admin)", description = "Passe le statut de EN_REVIEW à VALIDE. Notifie le proposeur.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/approuver")
    public ResponseEntity<RevenuResponse> approuver(@PathVariable Long id) {
        return ResponseEntity.ok(revenuService.approuver(id));
    }

    @Operation(summary = "Refuser une déclaration de revenu (admin)", description = "Passe le statut à REFUSE avec motif. Notifie le proposeur.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/refuser")
    public ResponseEntity<RevenuResponse> refuser(
            @PathVariable Long id,
            @Valid @RequestBody RefusRevenuRequest request) {
        return ResponseEntity.ok(revenuService.refuser(id, request.motif()));
    }
}
