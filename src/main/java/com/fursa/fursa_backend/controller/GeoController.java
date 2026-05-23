package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.model.enumeration.Pays;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1 (reunion Hugh 22/05/2026) : endpoints pays / villes pour les dropdowns
 * dynamiques du formulaire de creation de bien.
 *
 * MVP : liste figee de 10 pays cibles FURSA + leurs villes principales.
 * V2 : remplacer par une integration API externe (countrystatecity.in).
 */
@RestController
@RequestMapping("/api/geo")
@Tag(name = "Geo", description = "Pays et villes supportes par FURSA (dropdowns formulaire bien)")
@SecurityRequirements
public class GeoController {

    @Operation(summary = "Liste des pays cibles FURSA",
            description = "Retourne les pays supportes (code ISO 2 lettres, nom FR, devise locale ISO 3 lettres).")
    @GetMapping("/pays")
    public ResponseEntity<List<Map<String, String>>> listerPays() {
        List<Map<String, String>> out = new java.util.ArrayList<>();
        for (Pays p : Pays.values()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", p.name());
            m.put("nom", p.getNomFR());
            m.put("devise", p.getDeviseISO());
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    @Operation(summary = "Villes principales d'un pays",
            description = "Retourne les villes principales du pays. Si le code ne matche pas, retourne une liste vide.")
    @GetMapping("/pays/{code}/villes")
    public ResponseEntity<List<String>> villesParPays(@PathVariable String code) {
        try {
            Pays p = Pays.valueOf(code.toUpperCase());
            return ResponseEntity.ok(p.getVillesPrincipales());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(List.of());
        }
    }
}
