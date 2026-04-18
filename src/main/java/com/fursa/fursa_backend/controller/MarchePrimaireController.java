package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.AchatResponse;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/marche-primaire")
public class MarchePrimaireController {

    private final MarchePrimaireService marchePrimaireService;

    public MarchePrimaireController(MarchePrimaireService marchePrimaireService) {
        this.marchePrimaireService = marchePrimaireService;
    }

    /**
     * POST /api/marche-primaire/acheter
     * Endpoint pour acheter des parts d'une propriété
     */
    @PostMapping("/acheter")
    public ResponseEntity<AchatResponse> acheterParts(@RequestBody AchatRequest request) {
        AchatResponse response = marchePrimaireService.acheterParts(request);
        return ResponseEntity.ok(response);
    }
}
