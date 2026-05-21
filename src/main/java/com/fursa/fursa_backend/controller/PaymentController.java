package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.PaymentInitResponse;
import com.fursa.fursa_backend.dto.PaymentSessionStatusResponse;
import com.fursa.fursa_backend.model.PaymentSession;
import com.fursa.fursa_backend.model.Transaction;
import com.fursa.fursa_backend.repository.PaymentSessionRepository;
import com.fursa.fursa_backend.repository.TransactionRepository;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paiements")
@RequiredArgsConstructor
@Tag(name = "Paiements", description = "Initialisation de paiement via PSP (Mock/Yellow Card) + consultation du statut")
public class PaymentController {

    private final MarchePrimaireService marchePrimaireService;
    private final PaymentSessionRepository paymentSessionRepository;
    private final TransactionRepository transactionRepository;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Value("${blockchain.chain-id:11155111}")
    private long chainId;

    @Operation(
            summary = "Initier un paiement pour acheter des parts",
            description = """
                Cree une session de paiement chez le PSP actif et renvoie l'URL du widget vers laquelle
                rediriger l'investisseur. Le paiement reel est confirme de maniere asynchrone via webhook
                (POST /api/webhooks/{provider}).

                Header recommande : `Idempotency-Key` (UUID) pour resister aux double-clics et retries reseau.
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session creee, rediriger vers widgetUrl"),
            @ApiResponse(responseCode = "400", description = "Parts insuffisantes / propriete non publiee / nombre invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifie"),
            @ApiResponse(responseCode = "403", description = "Comptes admin bloques (delit d'initie)"),
            @ApiResponse(responseCode = "404", description = "Propriete introuvable")
    })
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping("/init")
    public ResponseEntity<PaymentInitResponse> init(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AchatRequest request) {
        Long investisseurId = authInvestisseur.currentId();
        return ResponseEntity.ok(marchePrimaireService.initierAchat(investisseurId, request, idempotencyKey));
    }

    @Operation(
            summary = "Consulter le statut d'une session de paiement",
            description = "Polling cote front toutes les 10s pour detecter la confirmation. Self uniquement (sauf admin).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut renvoye"),
            @ApiResponse(responseCode = "403", description = "Pas le proprietaire de la session"),
            @ApiResponse(responseCode = "404", description = "Session introuvable")
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/session/{id}")
    public ResponseEntity<PaymentSessionStatusResponse> getSession(@PathVariable Long id) {
        PaymentSession session = paymentSessionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PaymentSession introuvable : " + id));

        Long currentUserId = authInvestisseur.currentId();
        if (!session.getInvestisseur().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Cette session ne vous appartient pas");
        }

        // Recupere le vrai hash on-chain depuis la Transaction associee
        String txHash = null;
        String etherscanUrl = null;
        if (session.getTransactionId() != null) {
            Transaction tx = transactionRepository.findById(session.getTransactionId()).orElse(null);
            if (tx != null) {
                txHash = tx.getHashTransaction();
                // On expose Etherscan uniquement pour un vrai hash on-chain (0x + 64 hex chars)
                if (txHash != null && txHash.matches("^0x[0-9a-fA-F]{64}$")) {
                    String etherscanDomain = (chainId == 1L) ? "etherscan.io"
                            : (chainId == 11155111L) ? "sepolia.etherscan.io"
                            : (chainId == 137L) ? "polygonscan.com"
                            : null;
                    if (etherscanDomain != null) {
                        etherscanUrl = "https://" + etherscanDomain + "/tx/" + txHash;
                    }
                }
            }
        }

        return ResponseEntity.ok(new PaymentSessionStatusResponse(
                session.getId(),
                session.getExternalId(),
                session.getStatut().name(),
                txHash,
                etherscanUrl,
                session.getErrorMessage(),
                session.getExpiresAt(),
                session.getConfirmedAt()
        ));
    }
}
