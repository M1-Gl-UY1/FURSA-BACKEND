package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AjustementWalletRequest;
import com.fursa.fursa_backend.dto.WalletResponse;
import com.fursa.fursa_backend.dto.WalletTransactionResponse;
import com.fursa.fursa_backend.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/wallets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Wallets", description = "Administration des wallets utilisateurs (consultation + ajustements)")
public class AdminWalletController {

    private final WalletService walletService;

    @Operation(summary = "Liste tous les wallets",
            description = "Retourne tous les wallets de la plateforme avec leur solde courant et user attache.")
    @GetMapping
    public ResponseEntity<List<WalletResponse>> lister() {
        return ResponseEntity.ok(walletService.listAllWallets());
    }

    @Operation(summary = "Wallet d'un user specifique",
            description = "Detail d'un wallet par userId.")
    @GetMapping("/user/{userId}")
    public ResponseEntity<WalletResponse> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.toResponse(
                walletService.findByUserId(userId)
                        .orElseThrow(() -> new EntityNotFoundException("Aucun wallet pour user " + userId))
        ));
    }

    @Operation(summary = "Historique d'un wallet (admin)",
            description = "Historique complet des mouvements d'un wallet par userId.")
    @GetMapping("/user/{userId}/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> historique(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.listTransactionsForAdmin(userId));
    }

    /**
     * V2 BB (08/06/2026) : endpoint d'ajustement manuel DESACTIVE.
     * Decision PO : l'admin n'a plus le droit de crediter/debiter un wallet.
     * Tout mouvement de wallet passe desormais par les flows metier (achat,
     * vente, dividende, recharge PSP, retrait valide). Le code de
     * walletService.ajustementAdmin reste disponible pour des scripts ops
     * one-shot si besoin, mais n'est plus expose en API.
     */
    @Operation(summary = "[DEPRECATED] Ajustement manuel admin",
            description = "Endpoint retire en V2 BB. Renvoie 410 Gone.")
    @PostMapping("/user/{userId}/ajuster")
    public ResponseEntity<Void> ajuster(
            @PathVariable Long userId,
            @Valid @RequestBody AjustementWalletRequest req) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.GONE).build();
    }
}
