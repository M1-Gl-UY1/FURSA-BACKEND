package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.service.DistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/distribution")
@RequiredArgsConstructor
public class DistributionController {

    private final DistributionService distributionService;

    @PostMapping("/{revenuId}")
    public ResponseEntity<List<Dividende>> distribuer(@PathVariable Long revenuId) {
        return ResponseEntity.ok(distributionService.distribuer(revenuId));
    }
}
