package com.fursa.fursa_backend.dividend_Calculation.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@AllArgsConstructor
@RequestMapping("/api/distribution")
public class DistributionServiceRestController {
    private final com.fursa.fursa_backend.dividend_Calculation.services.DistributionService distributionService;

    @GetMapping("/{revenuId}")
    public String lancerLaDistribution(@RequestParam Long revenuIdLong) {
        distributionService.distribuer(revenuIdLong);
        return "Distribution lancée avec succès";
    }
    

}
