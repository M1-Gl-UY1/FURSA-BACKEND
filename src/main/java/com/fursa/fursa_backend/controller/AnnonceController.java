package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AnnonceRequest;
import com.fursa.fursa_backend.dto.AnnonceResponse;
import com.fursa.fursa_backend.service.AnnonceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/annonces")
@RequiredArgsConstructor
public class AnnonceController {

    private final AnnonceService annonceService;

    @PostMapping
    public ResponseEntity<AnnonceResponse> creer(@Valid @RequestBody AnnonceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(annonceService.creer(request));
    }

    @GetMapping
    public ResponseEntity<List<AnnonceResponse>> listerOuvertes() {
        return ResponseEntity.ok(annonceService.listerOuvertes());
    }

    @GetMapping("/vendeur/{vendeurId}")
    public ResponseEntity<List<AnnonceResponse>> listerParVendeur(@PathVariable Long vendeurId) {
        return ResponseEntity.ok(annonceService.listerParVendeur(vendeurId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnnonceResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(annonceService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AnnonceResponse> annuler(@PathVariable Long id, @RequestParam Long vendeurId) {
        return ResponseEntity.ok(annonceService.annuler(id, vendeurId));
    }
}
