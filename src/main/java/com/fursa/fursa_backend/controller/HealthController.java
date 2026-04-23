package com.fursa.fursa_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Verification de la sante de l'API (utilise par la CI/CD)")
public class HealthController {

    @Operation(summary = "Ping", description = "Retourne {\"status\":\"UP\"} si l'API tourne.")
    @SecurityRequirements
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
