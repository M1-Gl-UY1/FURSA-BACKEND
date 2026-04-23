package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.AchatResponse;
import com.fursa.fursa_backend.dto.PaiementResponse;
import com.fursa.fursa_backend.dto.PossessionResponse;
import com.fursa.fursa_backend.dto.TransactionResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/marche-primaire")
@RequiredArgsConstructor
public class MarchePrimaireController {

    private final MarchePrimaireService marchePrimaireService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @PostMapping("/acheter")
    public ResponseEntity<AchatResponse> acheterParts(@RequestBody AchatRequest request) {
        Long investisseurId = authInvestisseur.currentId();
        return ResponseEntity.ok(marchePrimaireService.acheterParts(investisseurId, request));
    }

    @GetMapping("/me/possessions")
    public ResponseEntity<List<PossessionResponse>> mesPossessions() {
        return ResponseEntity.ok(marchePrimaireService.getPortefeuille(authInvestisseur.currentId()));
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<List<TransactionResponse>> mesTransactions() {
        return ResponseEntity.ok(marchePrimaireService.getTransactions(authInvestisseur.currentId()));
    }

    @GetMapping("/me/paiements")
    public ResponseEntity<List<PaiementResponse>> mesPaiements() {
        return ResponseEntity.ok(marchePrimaireService.getPaiements(authInvestisseur.currentId()));
    }

    @GetMapping("/possessions")
    public ResponseEntity<List<PossessionResponse>> getAllPossessions() {
        return ResponseEntity.ok(marchePrimaireService.getAllPossessions());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(marchePrimaireService.getAllTransactions());
    }

    @GetMapping("/paiements")
    public ResponseEntity<List<PaiementResponse>> getAllPaiements() {
        return ResponseEntity.ok(marchePrimaireService.getAllPaiements());
    }

    @GetMapping("/possessions/{investisseurId}")
    public ResponseEntity<List<PossessionResponse>> getPortefeuille(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getPortefeuille(investisseurId));
    }

    @GetMapping("/transactions/{investisseurId}")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getTransactions(investisseurId));
    }

    @GetMapping("/paiements/{investisseurId}")
    public ResponseEntity<List<PaiementResponse>> getPaiements(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getPaiements(investisseurId));
    }
}
