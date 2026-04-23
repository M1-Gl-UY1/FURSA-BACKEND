package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.service.DistributionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/distribution")
@RequiredArgsConstructor
@Tag(name = "Distribution des revenus", description = "Calcul des dividendes au prorata des possessions (Idriss)")
public class DistributionController {

    private final DistributionService distributionService;

    @Operation(
            summary = "Distribuer un revenu",
            description = """
                    Cree un Dividende par investisseur possedant des parts de la propriete liee au revenu.
                    Montant = montantTotal * partsDuInvestisseur / nombreTotalPartsDeLaPropriete (arrondi HALF_UP a 2 decimales).
                    Chaque dividende est persiste avec statut = VALIDE et un hashTransaction UUID.
                    Transactionnel : tout ou rien.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dividendes crees"),
            @ApiResponse(responseCode = "400", description = "Aucune propriete, aucune possession, ou nombre total de parts invalide"),
            @ApiResponse(responseCode = "404", description = "Revenu inconnu")
    })
    @PostMapping("/{revenuId}")
    public ResponseEntity<List<Dividende>> distribuer(@PathVariable Long revenuId) {
        return ResponseEntity.ok(distributionService.distribuer(revenuId));
    }
}
