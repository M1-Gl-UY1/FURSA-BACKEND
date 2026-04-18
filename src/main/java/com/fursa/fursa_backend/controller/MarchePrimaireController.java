package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.*;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/marche-primaire")
public class MarchePrimaireController {

    private final MarchePrimaireService marchePrimaireService;

    public MarchePrimaireController(MarchePrimaireService marchePrimaireService) {
        this.marchePrimaireService = marchePrimaireService;
    }

    /**
     * POST /api/marche-primaire/acheter
     * Endpoint pour acheter des parts d'une propriété
     */
    @PostMapping("/acheter")
    public ResponseEntity<AchatResponse> acheterParts(@RequestBody AchatRequest request) {
        AchatResponse response = marchePrimaireService.acheterParts(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/marche-primaire/possessions
     * Toutes les possessions
     */
    @GetMapping("/possessions")
    public ResponseEntity<List<PossessionResponse>> getAllPossessions() {
        return ResponseEntity.ok(marchePrimaireService.getAllPossessions());
    }

    /**
     * GET /api/marche-primaire/transactions
     * Toutes les transactions
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(marchePrimaireService.getAllTransactions());
    }

    /**
     * GET /api/marche-primaire/paiements
     * Tous les paiements
     */
    @GetMapping("/paiements")
    public ResponseEntity<List<PaiementResponse>> getAllPaiements() {
        return ResponseEntity.ok(marchePrimaireService.getAllPaiements());
    }

    /**
     * GET /api/marche-primaire/possessions/{investisseurId}
     * Consulter le portefeuille d'un investisseur
     */
    @GetMapping("/possessions/{investisseurId}")
    public ResponseEntity<List<PossessionResponse>> getPortefeuille(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getPortefeuille(investisseurId));
    }

    /**
     * GET /api/marche-primaire/transactions/{investisseurId}
     * Historique des transactions d'un investisseur
     */
    @GetMapping("/transactions/{investisseurId}")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getTransactions(investisseurId));
    }

    /**
     * GET /api/marche-primaire/paiements/{investisseurId}
     * Historique des paiements d'un investisseur
     */
    @GetMapping("/paiements/{investisseurId}")
    public ResponseEntity<List<PaiementResponse>> getPaiements(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getPaiements(investisseurId));
    }
}
