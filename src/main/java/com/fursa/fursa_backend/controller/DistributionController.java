package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.DistributionPreviewItem;
import com.fursa.fursa_backend.dto.DividendeResponse;
import com.fursa.fursa_backend.service.DistributionService;
import com.fursa.fursa_backend.service.DividendeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/distribution")
@RequiredArgsConstructor
@Tag(name = "Distribution des revenus", description = "Calcul des dividendes au prorata des possessions")
public class DistributionController {

    private final DistributionService distributionService;
    private final DividendeQueryService dividendeQuery;

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
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{revenuId}")
    public ResponseEntity<List<DividendeResponse>> distribuer(@PathVariable Long revenuId) {
        distributionService.distribuer(revenuId);
        return ResponseEntity.ok(dividendeQuery.listerPourRevenu(revenuId));
    }

    @Operation(
            summary = "Preview de la distribution (sans persister)",
            description = """
                    Renvoie la liste des investisseurs qui recevront un dividende avec le calcul prorata,
                    SANS rien persister en base. Permet a l'admin de valider visuellement la repartition
                    avant de declencher la distribution effective.

                    Retour : { items: [...], totalAttendu, totalInvestisseurs, revenuId }""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preview calculee"),
            @ApiResponse(responseCode = "400", description = "Propriete invalide ou parts manquantes"),
            @ApiResponse(responseCode = "404", description = "Revenu inconnu")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{revenuId}/preview")
    public ResponseEntity<Map<String, Object>> preview(@PathVariable Long revenuId) {
        List<DistributionPreviewItem> items = distributionService.preview(revenuId);
        BigDecimal totalAttendu = items.stream()
                .map(DistributionPreviewItem::montantAttendu)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ResponseEntity.ok(Map.of(
                "revenuId", revenuId,
                "items", items,
                "totalInvestisseurs", items.size(),
                "totalAttendu", totalAttendu
        ));
    }
}
