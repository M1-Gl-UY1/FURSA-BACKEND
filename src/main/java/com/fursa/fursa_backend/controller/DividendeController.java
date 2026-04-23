package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.DividendeResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.DividendeQueryService;
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

@RestController
@RequestMapping("/api/dividendes")
@RequiredArgsConstructor
@Tag(name = "Dividendes", description = "Consultation des dividendes distribues")
public class DividendeController {

    private final DividendeQueryService dividendeQuery;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(summary = "Mes dividendes", description = "Dividendes percus par l'investisseur connecte.")
    @GetMapping("/me")
    public ResponseEntity<List<DividendeResponse>> mesDividendes() {
        return ResponseEntity.ok(dividendeQuery.listerPour(authInvestisseur.currentId()));
    }

    @Operation(summary = "Dividendes d'un investisseur (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/investisseur/{investisseurId}")
    public ResponseEntity<List<DividendeResponse>> dividendesInvestisseur(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(dividendeQuery.listerPour(investisseurId));
    }

    @Operation(summary = "Dividendes generes par un revenu", description = "Utile pour verifier une distribution apres coup.")
    @GetMapping("/revenu/{revenuId}")
    public ResponseEntity<List<DividendeResponse>> dividendesRevenu(@PathVariable Long revenuId) {
        return ResponseEntity.ok(dividendeQuery.listerPourRevenu(revenuId));
    }

    @Operation(summary = "Tous les dividendes (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<DividendeResponse>> tous() {
        return ResponseEntity.ok(dividendeQuery.listerTous());
    }
}
