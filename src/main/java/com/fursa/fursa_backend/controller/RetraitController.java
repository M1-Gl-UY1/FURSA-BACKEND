package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.DemandeRetraitRequest;
import com.fursa.fursa_backend.dto.DemandeRetraitResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.RetraitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Phase 10e : endpoints user pour la gestion des demandes de retrait.
 */
@RestController
@RequestMapping("/api/retraits")
@RequiredArgsConstructor
@Tag(name = "Retraits", description = "Demandes de retrait des fonds (wallet ou escrow propriete)")
public class RetraitController {

    private final RetraitService retraitService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(summary = "Creer une demande de retrait",
            description = """
                    Cree une demande de retrait soit depuis le wallet (vers MM / virement / crypto)
                    soit depuis l'escrow d'une propriete (vers wallet proprio).
                    Le montant est immediatement reserve (debit). Validation admin requise.""")
    @PostMapping
    public ResponseEntity<DemandeRetraitResponse> demander(@Valid @RequestBody DemandeRetraitRequest req) {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.status(HttpStatus.CREATED).body(retraitService.demander(userId, req));
    }

    @Operation(summary = "Mes demandes de retrait",
            description = "Historique des demandes de retrait du user authentifie.")
    @GetMapping("/me")
    public ResponseEntity<List<DemandeRetraitResponse>> mesDemandes() {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.ok(retraitService.mesDemandes(userId));
    }
}
