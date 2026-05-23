package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.EscrowProprieteResponse;
import com.fursa.fursa_backend.dto.EscrowTransactionResponse;
import com.fursa.fursa_backend.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 10c : etat de la collecte crowdfunding d'une propriete (escrow).
 *
 * Endpoint public (lecture) pour afficher la progression. Mouvements detailles
 * reserves a l'admin.
 */
@RestController
@RequestMapping("/api/escrow")
@RequiredArgsConstructor
@Tag(name = "Escrow propriete", description = "Etat de la collecte crowdfunding par propriete")
public class EscrowController {

    private final EscrowService escrowService;

    @Operation(summary = "Statut de la collecte d'une propriete",
            description = """
                    Retourne le solde escrow, le total collecte, le pourcentage de l'objectif,
                    le statut (EN_COLLECTE / FINANCEE / ANNULEE) et le seuil de declenchement (80%).""")
    @GetMapping("/propriete/{proprieteId}")
    public ResponseEntity<EscrowProprieteResponse> statut(@PathVariable Long proprieteId) {
        return ResponseEntity.ok(escrowService.getStatut(proprieteId));
    }

    @Operation(summary = "Historique des mouvements d'un escrow (admin)",
            description = "Journal append-only de tous les credits et debits d'un escrow propriete.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/propriete/{proprieteId}/transactions")
    public ResponseEntity<List<EscrowTransactionResponse>> historique(@PathVariable Long proprieteId) {
        return ResponseEntity.ok(escrowService.historique(proprieteId));
    }

    @Operation(summary = "Liste de tous les escrows (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<EscrowProprieteResponse>> tous() {
        return ResponseEntity.ok(escrowService.listerTous());
    }
}
