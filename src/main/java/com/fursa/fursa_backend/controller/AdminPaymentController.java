package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AdminPaymentSessionResponse;
import com.fursa.fursa_backend.model.PaymentSession;
import com.fursa.fursa_backend.model.enumeration.StatutPaymentSession;
import com.fursa.fursa_backend.repository.PaymentSessionRepository;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/paiements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Paiements", description = "Gestion admin des sessions de paiement (consultation, retry on-chain)")
public class AdminPaymentController {

    private final PaymentSessionRepository paymentSessionRepository;
    private final MarchePrimaireService marchePrimaireService;

    @Operation(summary = "Liste les sessions de paiement par statut",
            description = "Statuts possibles : PENDING, CONFIRMED, EXPIRED, FAILED. Tri par createdAt desc.")
    @GetMapping
    public ResponseEntity<List<AdminPaymentSessionResponse>> listByStatut(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "FAILED") StatutPaymentSession statut) {
        List<PaymentSession> sessions = paymentSessionRepository.findByStatutOrderByCreatedAtDesc(statut);
        return ResponseEntity.ok(sessions.stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Detail d'une session (audit)")
    @GetMapping("/{id}")
    public ResponseEntity<AdminPaymentSessionResponse> get(@PathVariable Long id) {
        PaymentSession session = paymentSessionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Session introuvable : " + id));
        return ResponseEntity.ok(toResponse(session));
    }

    @Operation(summary = "Retry on-chain pour une session CONFIRMED dont l'addInvestor a echoue",
            description = """
                    Cas typique : l'investisseur a paye, la session est CONFIRMED, mais l'errorMessage indique
                    "On-chain echoue : ...". Cet endpoint relance BlockchainService.addInvestor() et stocke
                    le vrai tx hash dans Transaction.hashTransaction.

                    Pre-requis : l'investisseur DOIT avoir un wallet_address renseigne.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retry reussi"),
            @ApiResponse(responseCode = "400", description = "Session pas dans le bon etat ou wallet manquant"),
            @ApiResponse(responseCode = "404", description = "Session introuvable"),
            @ApiResponse(responseCode = "500", description = "Echec on-chain (rate limit, gas, contrat...)")
    })
    @PostMapping("/{id}/retry-on-chain")
    public ResponseEntity<Map<String, Object>> retryOnChain(@PathVariable Long id) {
        marchePrimaireService.retryOnChain(id);
        return ResponseEntity.ok(Map.of("sessionId", id, "status", "retry_ok"));
    }

    private AdminPaymentSessionResponse toResponse(PaymentSession s) {
        return new AdminPaymentSessionResponse(
                s.getId(),
                s.getExternalId(),
                s.getInvestisseur() != null ? s.getInvestisseur().getId() : null,
                s.getInvestisseur() != null ? s.getInvestisseur().getEmail() : null,
                s.getPropriete() != null ? s.getPropriete().getId() : null,
                s.getPropriete() != null ? s.getPropriete().getNom() : null,
                s.getNombreParts(),
                s.getMontantFiat(),
                s.getDeviseFiat(),
                s.getMontantUsdc(),
                s.getProviderName(),
                s.getStatut().name(),
                s.getErrorMessage(),
                s.getCreatedAt(),
                s.getExpiresAt(),
                s.getConfirmedAt(),
                s.getPaiementId(),
                s.getTransactionId(),
                s.getPossessionId()
        );
    }
}
