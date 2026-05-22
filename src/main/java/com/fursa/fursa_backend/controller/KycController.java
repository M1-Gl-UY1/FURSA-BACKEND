package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.KycSubmissionResponse;
import com.fursa.fursa_backend.dto.KycSubmitRequest;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Endpoints KYC cote investisseur.
 *
 * Workflow :
 *   1. GET /api/kyc/me                 -> statut courant (NONE | PENDING | IN_REVIEW | APPROVED | REJECTED)
 *   2. POST /api/kyc/submit (multipart)-> soumet un nouveau dossier
 *   3. GET /api/kyc/me/history         -> historique complet
 */
@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INVESTISSEUR')")
@Tag(name = "KYC investisseur", description = "Soumission et consultation de son propre dossier KYC")
public class KycController {

    private final KycService kycService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(summary = "Statut courant du KYC (derniere soumission)")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Long id = authInvestisseur.currentId();
        return kycService.findMine(id)
                .map(r -> ResponseEntity.ok(Map.<String, Object>of("statut", r.statut(), "submission", r)))
                .orElseGet(() -> ResponseEntity.ok(Map.<String, Object>of("statut", "NONE", "submission", (Object) null)));
    }

    @Operation(summary = "Historique des soumissions KYC")
    @GetMapping("/me/history")
    public ResponseEntity<List<KycSubmissionResponse>> history() {
        return ResponseEntity.ok(kycService.findAllMine(authInvestisseur.currentId()));
    }

    @Operation(
            summary = "Soumettre un nouveau dossier KYC",
            description = """
                    Multipart obligatoire avec :
                    - `data` : JSON KycSubmitRequest (nationalite, dateNaissance, paysResidence, adresse, sourceFonds, isPep, declarationSurHonneur)
                    - `documentIdentite` : fichier (jpg/png/pdf, max 10 MB)
                    - `documentDomicile` : fichier (jpg/png/pdf, max 10 MB)
                    - `selfie` : fichier (jpg/png, max 10 MB)

                    Refuse si l'investisseur a deja un dossier PENDING/IN_REVIEW ou APPROVED.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Soumission creee, statut PENDING"),
            @ApiResponse(responseCode = "400", description = "Donnees ou fichiers invalides"),
            @ApiResponse(responseCode = "409", description = "Dossier KYC deja en cours ou approuve")
    })
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KycSubmissionResponse> submit(
            @Valid @RequestPart("data") KycSubmitRequest data,
            @RequestPart("documentIdentite") MultipartFile documentIdentite,
            @RequestPart("documentDomicile") MultipartFile documentDomicile,
            @RequestPart("selfie") MultipartFile selfie) {
        Long id = authInvestisseur.currentId();
        return ResponseEntity.ok(kycService.submit(id, data, documentIdentite, documentDomicile, selfie));
    }
}
