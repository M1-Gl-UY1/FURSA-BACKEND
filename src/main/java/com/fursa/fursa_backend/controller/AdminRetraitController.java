package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.DemandeRetraitResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.RetraitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 10e : endpoints admin pour valider / refuser / completer les retraits.
 */
@RestController
@RequestMapping("/api/admin/retraits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Retraits", description = "Administration des demandes de retrait")
public class AdminRetraitController {

    private final RetraitService retraitService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(summary = "Liste de toutes les demandes de retrait (admin)")
    @GetMapping
    public ResponseEntity<List<DemandeRetraitResponse>> toutes() {
        return ResponseEntity.ok(retraitService.toutesLesDemandes());
    }

    @Operation(summary = "Demandes en attente de validation")
    @GetMapping("/pending")
    public ResponseEntity<List<DemandeRetraitResponse>> enAttente() {
        return ResponseEntity.ok(retraitService.demandesEnAttente());
    }

    @Operation(summary = "Valider une demande de retrait",
            description = """
                    Calcule la commission FURSA (5%), credit du net :
                    - Si ESCROW_PROPRIETE -> credit wallet proprio, statut COMPLETED
                    - Si WALLET (cash externe) -> statut APPROVED, admin doit ensuite executer + marquerCompletee()""")
    @PostMapping("/{id}/valider")
    public ResponseEntity<DemandeRetraitResponse> valider(@PathVariable Long id) {
        return ResponseEntity.ok(retraitService.valider(id, authInvestisseur.currentId()));
    }

    @Operation(summary = "Refuser une demande de retrait",
            description = "Recredite le montant a la source. Body : { motif: 'min 5 chars' }")
    @PostMapping("/{id}/refuser")
    public ResponseEntity<DemandeRetraitResponse> refuser(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String motif = body == null ? null : body.get("motif");
        return ResponseEntity.ok(retraitService.refuser(id, authInvestisseur.currentId(), motif));
    }

    @Operation(summary = "Marquer une demande COMPLETED (apres execution cash)",
            description = """
                    A appeler quand l'admin a effectivement verse l'argent au user (MM, virement, crypto).
                    Body : { preuvePaiement: 'reference / lien' }""")
    @PostMapping("/{id}/completer")
    public ResponseEntity<DemandeRetraitResponse> completer(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String preuve = body == null ? null : body.get("preuvePaiement");
        return ResponseEntity.ok(retraitService.marquerCompletee(id, authInvestisseur.currentId(), preuve));
    }
}
