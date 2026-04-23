package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.AchatResponse;
import com.fursa.fursa_backend.dto.PaiementResponse;
import com.fursa.fursa_backend.dto.PossessionResponse;
import com.fursa.fursa_backend.dto.TransactionResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Marche primaire", description = "Achat de parts d'une propriete depuis le catalogue (Jorel)")
public class MarchePrimaireController {

    private final MarchePrimaireService marchePrimaireService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(
            summary = "Acheter des parts d'une propriete",
            description = "L'investisseur connecte (extrait du JWT) achete un nombre de parts. Cree un Paiement (EN_ATTENTE -> VALIDE), une Transaction (hash simule, SUCCES), met a jour ou cree la Possession et decremente les parts disponibles de la propriete. Transactionnel.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Achat reussi"),
            @ApiResponse(responseCode = "400", description = "Parts insuffisantes / propriete non publiee / nombre invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifie"),
            @ApiResponse(responseCode = "404", description = "Propriete ou investisseur introuvable")
    })
    @PostMapping("/acheter")
    public ResponseEntity<AchatResponse> acheterParts(@RequestBody AchatRequest request) {
        Long investisseurId = authInvestisseur.currentId();
        return ResponseEntity.ok(marchePrimaireService.acheterParts(investisseurId, request));
    }

    @Operation(summary = "Mon portefeuille", description = "Possessions de l'investisseur connecte.")
    @GetMapping("/me/possessions")
    public ResponseEntity<List<PossessionResponse>> mesPossessions() {
        return ResponseEntity.ok(marchePrimaireService.getPortefeuille(authInvestisseur.currentId()));
    }

    @Operation(summary = "Mes transactions", description = "Historique des transactions de l'investisseur connecte.")
    @GetMapping("/me/transactions")
    public ResponseEntity<List<TransactionResponse>> mesTransactions() {
        return ResponseEntity.ok(marchePrimaireService.getTransactions(authInvestisseur.currentId()));
    }

    @Operation(summary = "Mes paiements", description = "Historique des paiements de l'investisseur connecte.")
    @GetMapping("/me/paiements")
    public ResponseEntity<List<PaiementResponse>> mesPaiements() {
        return ResponseEntity.ok(marchePrimaireService.getPaiements(authInvestisseur.currentId()));
    }

    @Operation(summary = "Toutes les possessions (admin)")
    @GetMapping("/possessions")
    public ResponseEntity<List<PossessionResponse>> getAllPossessions() {
        return ResponseEntity.ok(marchePrimaireService.getAllPossessions());
    }

    @Operation(summary = "Toutes les transactions (admin)")
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(marchePrimaireService.getAllTransactions());
    }

    @Operation(summary = "Tous les paiements (admin)")
    @GetMapping("/paiements")
    public ResponseEntity<List<PaiementResponse>> getAllPaiements() {
        return ResponseEntity.ok(marchePrimaireService.getAllPaiements());
    }

    @Operation(summary = "Portefeuille d'un investisseur (admin)")
    @GetMapping("/possessions/{investisseurId}")
    public ResponseEntity<List<PossessionResponse>> getPortefeuille(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getPortefeuille(investisseurId));
    }

    @Operation(summary = "Historique des transactions d'un investisseur (admin)")
    @GetMapping("/transactions/{investisseurId}")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getTransactions(investisseurId));
    }

    @Operation(summary = "Historique des paiements d'un investisseur (admin)")
    @GetMapping("/paiements/{investisseurId}")
    public ResponseEntity<List<PaiementResponse>> getPaiements(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getPaiements(investisseurId));
    }
}
