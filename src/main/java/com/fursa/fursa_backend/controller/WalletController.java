package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.RechargeRequest;
import com.fursa.fursa_backend.dto.WalletResponse;
import com.fursa.fursa_backend.dto.WalletTransactionResponse;
import com.fursa.fursa_backend.model.enumeration.TypeWalletTransaction;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet utilisateur (solde + historique mouvements)")
public class WalletController {

    private final WalletService walletService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(summary = "Mon wallet",
            description = "Retourne le wallet de l'utilisateur courant. Le wallet est cree automatiquement a la premiere consultation si absent.")
    @GetMapping("/me")
    public ResponseEntity<WalletResponse> mon() {
        return ResponseEntity.ok(walletService.getResponseByUserId(authInvestisseur.currentId()));
    }

    @Operation(summary = "Statistiques wallet",
            description = "Solde, total credite, total debite, nombre de mouvements, date dernier mouvement.")
    @GetMapping("/me/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(walletService.stats(authInvestisseur.currentId()));
    }

    @Operation(summary = "Recharger mon wallet (mode demo)",
            description = """
                    Recharge le wallet de l'investisseur courant. MODE DEMO : aucun paiement reel
                    n'est encaisse, le solde est credite directement (type TOPUP). Plafond 10 000 par recharge.
                    A remplacer par une vraie integration PSP (Yellow Card / Mobile Money) avant mise en prod reelle.""")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping("/me/recharger")
    public ResponseEntity<WalletTransactionResponse> recharger(@Valid @RequestBody RechargeRequest request) {
        Long userId = authInvestisseur.currentId();
        WalletTransactionResponse tx = walletService.toResponse(
                walletService.rechargerMock(userId, request.montant(), request.methode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @Operation(summary = "Historique des mouvements",
            description = """
                    Liste tous les mouvements du wallet courant (ordre antichronologique).
                    Filtres optionnels : type, plage de dates.""")
    @GetMapping("/me/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> transactions(
            @RequestParam(required = false) TypeWalletTransaction type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Long userId = authInvestisseur.currentId();
        if (type == null && from == null && to == null) {
            return ResponseEntity.ok(walletService.listTransactions(userId));
        }
        return ResponseEntity.ok(walletService.listTransactionsFiltered(userId, type, from, to));
    }
}
