package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.PrixPartDiagnosticResponse;
import com.fursa.fursa_backend.service.PrixPartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 M (07/06/2026) : endpoints admin pour le prix dynamique.
 *
 * - GET /api/admin/prix-parts/constantes : table des constantes du modele (pour la page "Modele de prix").
 * - GET /api/admin/prix-parts/{proprieteId}/diagnostic : vue complete pour un bien (formule, valeurs courantes, historique).
 */
@RestController
@RequestMapping("/api/admin/prix-parts")
@RequiredArgsConstructor
@Tag(name = "Admin · Prix dynamique", description = "Constantes du modele + diagnostic par bien")
public class AdminPrixPartController {

    private final PrixPartService prixPartService;

    @Operation(summary = "Constantes du modele de prix dynamique",
            description = "Renvoie les coefficients et caps utilises par la formule. Source unique pour l'UI.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/constantes")
    public ResponseEntity<PrixPartDiagnosticResponse.ConstantesFormule> constantes() {
        return ResponseEntity.ok(PrixPartService.getConstantes());
    }

    @Operation(summary = "Diagnostic du prix d'un bien",
            description = "Vue complete : prix initial / courant, bonus rentabilite cumule, bonus demande, bornes plancher / plafond, constantes du modele et historique des recalculs.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{proprieteId}/diagnostic")
    public ResponseEntity<PrixPartDiagnosticResponse> diagnostic(@PathVariable Long proprieteId) {
        return ResponseEntity.ok(prixPartService.diagnostic(proprieteId));
    }
}
