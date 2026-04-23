package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AchatAnnonceRequest;
import com.fursa.fursa_backend.dto.AchatAnnonceResponse;
import com.fursa.fursa_backend.service.AnnonceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marche-secondaire")
@RequiredArgsConstructor
public class MarcheSecondaireController {

    private final AnnonceService annonceService;

    @PostMapping("/annonces/{annonceId}/acheter")
    public ResponseEntity<AchatAnnonceResponse> acheter(
            @PathVariable Long annonceId,
            @Valid @RequestBody AchatAnnonceRequest request) {
        return ResponseEntity.ok(annonceService.acheter(annonceId, request));
    }
}
