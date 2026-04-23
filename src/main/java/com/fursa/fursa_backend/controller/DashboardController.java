package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.DashboardAdminResponse;
import com.fursa.fursa_backend.dto.DashboardInvestisseurResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Agregats metier pour l'investisseur courant et pour l'admin")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(
            summary = "Mon dashboard",
            description = """
                    Retourne :
                    - nombre de proprietes differentes detenues
                    - total des parts detenues
                    - total investi (somme des paiements VALIDE)
                    - valeur portefeuille (parts * prix unitaire courant)
                    - total dividendes recus
                    - revenus annuels previsionnels (valeur portefeuille * rentabilite prevue)
                    - nombre d'annonces ouvertes
                    - nombre de notifications non lues
                    """)
    @GetMapping("/me")
    public ResponseEntity<DashboardInvestisseurResponse> monDashboard() {
        return ResponseEntity.ok(dashboardService.pourInvestisseur(authInvestisseur.currentId()));
    }

    @Operation(summary = "Dashboard d'un investisseur (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/investisseur/{investisseurId}")
    public ResponseEntity<DashboardInvestisseurResponse> dashboardInvestisseur(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(dashboardService.pourInvestisseur(investisseurId));
    }

    @Operation(
            summary = "Dashboard global (admin)",
            description = "Agregats plateforme : nb investisseurs, nb proprietes, parts vendues, volume transactions, dividendes distribues, annonces ouvertes.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<DashboardAdminResponse> dashboardAdmin() {
        return ResponseEntity.ok(dashboardService.global());
    }
}
