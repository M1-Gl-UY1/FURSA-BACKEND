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

    @Operation(summary = "Ajustement manuel admin",
            description = """
                    Permet d'ajuster manuellement le solde d'un wallet (correction d'erreur,
                    geste commercial, support utilisateur).
                    Le montant peut etre positif (credit) ou negatif (debit).
                    Le motif est obligatoire et trace dans l'historique pour l'audit.""")
    @PostMapping("/user/{userId}/ajuster")
    public ResponseEntity<WalletTransactionResponse> ajuster(
            @PathVariable Long userId,
            @Valid @RequestBody AjustementWalletRequest req) {
        var tx = walletService.ajustementAdmin(userId, req.montant(), req.motif());
        return ResponseEntity.ok(walletService.toResponse(tx));
    }
}
