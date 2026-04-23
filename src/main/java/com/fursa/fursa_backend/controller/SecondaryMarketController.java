package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.CreateAnnonceRequest;
import com.fursa.fursa_backend.dto.PurchaseResult;
import com.fursa.fursa_backend.model.Annonce;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.service.SecondaryMarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/secondary-market")
@RequiredArgsConstructor
@Slf4j
public class SecondaryMarketController {

    private final SecondaryMarketService secondaryMarketService;

    // ================= CREATE ANNONCE =================
    @PostMapping("/annonces")
    //@PreAuthorize("hasRole('INVESTISSEUR')")
    public ResponseEntity<Annonce> createAnnonce(
            @Valid @RequestBody CreateAnnonceRequest request,
            @AuthenticationPrincipal Investisseur investisseur) {

        log.info("Création annonce par investisseur: {}", investisseur.getId());

        Annonce annonce = secondaryMarketService.createAnnonce(
                request,
                investisseur.getId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(annonce);
    }

    // ================= ACHAT PARTS =================
    @PostMapping("/achat")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    public ResponseEntity<PurchaseResult> acheterParts(
            @Valid @RequestBody AchatRequest request,
            @AuthenticationPrincipal Investisseur investisseur) {

        log.info("Achat parts par investisseur: {} pour annonce: {}",
                investisseur.getId(),
                request.getAnnonceId()
        );

        PurchaseResult result = secondaryMarketService.acheterParts(
                request,
                investisseur.getId()
        );

        return ResponseEntity.ok(result);
    }

    // ================= CANCEL ANNONCE =================
    @DeleteMapping("/annonces/{annonceId}")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    public ResponseEntity<Void> cancelAnnonce(
            @PathVariable Long annonceId,
            @AuthenticationPrincipal Investisseur investisseur) {

        log.info("Annulation annonce: {} par investisseur: {}",
                annonceId,
                investisseur.getId()
        );

        secondaryMarketService.cancelAnnonce(
                annonceId,
                investisseur.getId()
        );

        return ResponseEntity.noContent().build();
    }
}