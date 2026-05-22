package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.KycAdminResponse;
import com.fursa.fursa_backend.dto.KycRejectRequest;
import com.fursa.fursa_backend.model.enumeration.StatutKyc;
import com.fursa.fursa_backend.repository.KycSubmissionRepository;
import com.fursa.fursa_backend.service.KycService;
import com.fursa.fursa_backend.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/kyc")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin KYC", description = "Examen et validation des dossiers KYC investisseurs")
public class AdminKycController {

    private final KycService kycService;
    private final KycSubmissionRepository kycRepository;

    @Operation(summary = "Compteurs par statut (dashboard)")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "pending", kycRepository.countByStatut(StatutKyc.PENDING),
                "inReview", kycRepository.countByStatut(StatutKyc.IN_REVIEW),
                "approved", kycRepository.countByStatut(StatutKyc.APPROVED),
                "rejected", kycRepository.countByStatut(StatutKyc.REJECTED),
                "expired", kycRepository.countByStatut(StatutKyc.EXPIRED)
        ));
    }

    @Operation(summary = "Liste des soumissions par statut",
            description = "Par defaut PENDING. Tri par submitted_at desc.")
    @GetMapping
    public ResponseEntity<List<KycAdminResponse>> list(
            @RequestParam(defaultValue = "PENDING") StatutKyc statut) {
        return ResponseEntity.ok(kycService.listByStatut(statut));
    }

    @Operation(summary = "Detail d'une soumission")
    @GetMapping("/{id}")
    public ResponseEntity<KycAdminResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(kycService.findByIdForAdmin(id));
    }

    @Operation(summary = "Marquer comme 'en cours d'examen' (lock optimiste pour eviter 2 admin sur le meme dossier)")
    @PostMapping("/{id}/in-review")
    public ResponseEntity<KycAdminResponse> markInReview(@PathVariable Long id,
                                                          @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(kycService.markInReview(id, admin.getId()));
    }

    @Operation(summary = "Approuver le dossier",
            description = "Passe statut = APPROVED et investisseur.isVerified = true. Investisseur debloque pour acheter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approuve"),
            @ApiResponse(responseCode = "400", description = "Statut actuel non approuvable"),
            @ApiResponse(responseCode = "404", description = "Dossier introuvable")
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<KycAdminResponse> approve(@PathVariable Long id,
                                                     @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(kycService.approve(id, admin.getId()));
    }

    @Operation(summary = "Refuser le dossier (motif obligatoire)",
            description = "L'investisseur peut re-soumettre apres correction. Le motif est expose dans son interface.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<KycAdminResponse> reject(@PathVariable Long id,
                                                    @Valid @RequestBody KycRejectRequest body,
                                                    @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(kycService.reject(id, admin.getId(), body.motif()));
    }
}
