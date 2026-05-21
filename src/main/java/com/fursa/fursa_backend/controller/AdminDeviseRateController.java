package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.DeviseRateResponse;
import com.fursa.fursa_backend.dto.DeviseRateUpdateRequest;
import com.fursa.fursa_backend.model.DeviseRate;
import com.fursa.fursa_backend.repository.DeviseRateRepository;
import com.fursa.fursa_backend.service.DeviseRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/devise-rate")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Taux Devises", description = "Gestion des taux de conversion fiat -> USDC")
public class AdminDeviseRateController {

    private final DeviseRateRepository repository;
    private final DeviseRateService service;

    @Operation(summary = "Lister tous les taux de change")
    @GetMapping
    public ResponseEntity<List<DeviseRateResponse>> list() {
        List<DeviseRateResponse> taux = repository.findAll().stream()
                .map(this::toResponse)
                .sorted((a, b) -> a.codeDevise().compareTo(b.codeDevise()))
                .toList();
        return ResponseEntity.ok(taux);
    }

    @Operation(summary = "Detail d'un taux par code devise (XAF, EUR, USD...)")
    @GetMapping("/{code}")
    public ResponseEntity<DeviseRateResponse> get(@PathVariable String code) {
        DeviseRate r = repository.findById(code.toUpperCase())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Devise inconnue : " + code));
        return ResponseEntity.ok(toResponse(r));
    }

    @Operation(summary = "Mettre a jour un taux (ou en creer un nouveau)",
            description = "Idempotent : appel l'upsert. Le timestamp updated_at est rafraichi automatiquement.")
    @PutMapping("/{code}")
    public ResponseEntity<DeviseRateResponse> upsert(
            @PathVariable String code,
            @Valid @RequestBody DeviseRateUpdateRequest req) {
        DeviseRate saved = service.upsert(code, req.tauxVersUsdc());
        return ResponseEntity.ok(toResponse(saved));
    }

    private DeviseRateResponse toResponse(DeviseRate r) {
        return new DeviseRateResponse(r.getCodeDevise(), r.getTauxVersUsdc(), r.getUpdatedAt());
    }
}
