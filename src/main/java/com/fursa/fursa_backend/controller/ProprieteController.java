package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.dto.ProprieteResponse;
import com.fursa.fursa_backend.mapper.ProprieteMapper;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.service.BlockchainService;
import com.fursa.fursa_backend.service.ProprieteService;
import com.fursa.fursa_backend.service.TokenisationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proprietes")
@RequiredArgsConstructor
public class ProprieteController {

    private final ProprieteService proprieteService;
    private final ProprieteMapper proprieteMapper;
    private final BlockchainService blockchainService;
    private final TokenisationService tokenisationService;

    @PostMapping(value = "/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> ajouter(
            @RequestPart("propriete") @Valid ProprieteRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        Propriete created = proprieteService.creerPropriete(request, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proprieteMapper.toResponse(created));
    }

    @PutMapping(value = "/admin/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> modifier(
            @PathVariable Long id,
            @RequestPart("propriete") @Valid ProprieteRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        Propriete updated = proprieteService.modifierPropriete(id, request, files);
        return ResponseEntity.ok(proprieteMapper.toResponse(updated));
    }

    @GetMapping("/public")
    public ResponseEntity<List<ProprieteResponse>> list() {
        List<ProprieteResponse> result = proprieteService.listerTout()
                .stream().map(proprieteMapper::toResponse).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<ProprieteResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(
                proprieteMapper.toResponse(proprieteService.detail(id))
        );
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        proprieteService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/public/blockchain/status")
    public ResponseEntity<Map<String, Object>> blockchainStatus() {
    Map<String, Object> status = new HashMap<>();
    status.put("connecte", blockchainService.estConnecte());
    status.put("partsDisponibles", blockchainService.getPartsDisponibles());
    return ResponseEntity.ok(status);
}

@PostMapping("/admin/{id}/tokeniser")
public ResponseEntity<ProprieteResponse> tokeniser(@PathVariable Long id) throws Exception {
    Propriete tokenisee = tokenisationService.tokeniserPropriete(id);
    return ResponseEntity.ok(proprieteMapper.toResponse(tokenisee));
}
}