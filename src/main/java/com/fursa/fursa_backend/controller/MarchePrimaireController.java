package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.AchatResponse;
import com.fursa.fursa_backend.dto.InvestisseurPossessionResponse;
import com.fursa.fursa_backend.dto.PaiementResponse;
import com.fursa.fursa_backend.dto.PossessionResponse;
import com.fursa.fursa_backend.dto.TransactionResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/marche-primaire")
@RequiredArgsConstructor
@Tag(name = "Marche primaire", description = "Achat de parts d'une propriete depuis le catalogue")
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
    // Bloque les comptes ADMIN : conflit d'intérêt + délit d'initié + régulation financière.
    // Seuls les comptes INVESTISSEUR peuvent acheter des parts.
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping("/acheter")
    public ResponseEntity<AchatResponse> acheterParts(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AchatRequest request) {
        Long investisseurId = authInvestisseur.currentId();
        return ResponseEntity.ok(
                marchePrimaireService.acheterParts(investisseurId, request, idempotencyKey));
    }

    @Operation(
            summary = "Acheter des parts via wallet (Phase 10c - crowdfunding escrow)",
            description = """
                    Achat instantane par debit du wallet interne FURSA.
                    Verifie KYC + solde wallet >= prixUnitaire * nombreParts.
                    Cree la Possession en statut PENDING (activee quand collecte atteint 80%, Phase 10c bis).
                    L'argent va sur l'escrow de la propriete, pas sur le wallet du proprietaire.
                    Le proprietaire ne recevra son cash qu'apres validation d'une demande de retrait par l'admin (Phase 10e).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Achat reussi"),
            @ApiResponse(responseCode = "400", description = "Solde insuffisant / parts insuffisantes / collecte annulee / KYC manquant"),
            @ApiResponse(responseCode = "401", description = "Non authentifie"),
            @ApiResponse(responseCode = "403", description = "Acces refuse (admin ne peut pas acheter)"),
            @ApiResponse(responseCode = "404", description = "Propriete ou investisseur introuvable")
    })
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping("/acheter-via-wallet")
    public ResponseEntity<AchatResponse> acheterViaWallet(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AchatRequest request) {
        Long investisseurId = authInvestisseur.currentId();
        return ResponseEntity.ok(
                marchePrimaireService.acheterViaWallet(investisseurId, request, idempotencyKey));
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
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/possessions")
    public ResponseEntity<List<PossessionResponse>> getAllPossessions() {
        return ResponseEntity.ok(marchePrimaireService.getAllPossessions());
    }

    @Operation(summary = "Toutes les transactions (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(marchePrimaireService.getAllTransactions());
    }

    @Operation(summary = "Tous les paiements (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/paiements")
    public ResponseEntity<List<PaiementResponse>> getAllPaiements() {
        return ResponseEntity.ok(marchePrimaireService.getAllPaiements());
    }

    @Operation(summary = "Portefeuille d'un investisseur (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/possessions/{investisseurId}")
    public ResponseEntity<List<PossessionResponse>> getPortefeuille(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getPortefeuille(investisseurId));
    }

    @Operation(summary = "Historique des transactions d'un investisseur (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/transactions/{investisseurId}")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getTransactions(investisseurId));
    }

    @Operation(summary = "Historique des paiements d'un investisseur (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/paiements/{investisseurId}")
    public ResponseEntity<List<PaiementResponse>> getPaiements(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(marchePrimaireService.getPaiements(investisseurId));
    }

    @Operation(
            summary = "Investisseurs d'une propriete + pourcentage de parts detenu",
            description = "Liste les investisseurs ayant achete des parts de cette propriete, avec leur nombre de parts et leur pourcentage. Accessible a l'admin ou au proprietaire qui a propose le bien.")
    @PreAuthorize("hasRole('ADMIN') or @proprieteSecurity.isProposeur(#proprieteId, principal.id)")
    @GetMapping("/proprietes/{proprieteId}/investisseurs")
    public ResponseEntity<List<InvestisseurPossessionResponse>> getInvestisseursParPropriete(
            @PathVariable Long proprieteId) {
        return ResponseEntity.ok(marchePrimaireService.getInvestisseursParPropriete(proprieteId));
    }
}
