package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.WalletResponse;
import com.fursa.fursa_backend.dto.WalletTransactionResponse;
import com.fursa.fursa_backend.model.enumeration.TypeWalletTransaction;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
