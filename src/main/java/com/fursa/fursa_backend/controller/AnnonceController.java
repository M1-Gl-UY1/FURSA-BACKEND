package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AnnonceRequest;
import com.fursa.fursa_backend.dto.AnnonceResponse;
import com.fursa.fursa_backend.dto.AnnonceUpdateRequest;
import com.fursa.fursa_backend.service.AnnonceService;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/annonces")
@RequiredArgsConstructor
@Tag(name = "Annonces (marche secondaire)", description = "Publication d'annonces de revente entre investisseurs")
public class AnnonceController {

    private final AnnonceService annonceService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(
            summary = "Publier une annonce de revente",
            description = "Le vendeur (extrait du JWT) met en vente un nombre de parts a un prix unitaire. Verifie que le vendeur possede assez de parts (en tenant compte des annonces OUVERTE deja actives).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Annonce creee"),
            @ApiResponse(responseCode = "400", description = "Parts insuffisantes"),
            @ApiResponse(responseCode = "404", description = "Investisseur ou propriete inconnue")
    })
    @PostMapping
    public ResponseEntity<AnnonceResponse> creer(@Valid @RequestBody AnnonceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(annonceService.creer(authInvestisseur.currentId(), request));
    }

    @Operation(summary = "Lister les annonces OUVERTE", description = "Marche secondaire : toutes les annonces en cours.")
    @GetMapping
    public ResponseEntity<List<AnnonceResponse>> listerOuvertes() {
        return ResponseEntity.ok(annonceService.listerOuvertes());
    }

    @Operation(summary = "Mes annonces", description = "Toutes les annonces publiees par l'investisseur connecte (tous statuts).")
    @GetMapping("/me")
    public ResponseEntity<List<AnnonceResponse>> mesAnnonces() {
        return ResponseEntity.ok(annonceService.listerParVendeur(authInvestisseur.currentId()));
    }

    @Operation(summary = "Annonces d'un vendeur (admin)")
    @GetMapping("/vendeur/{vendeurId}")
    public ResponseEntity<List<AnnonceResponse>> listerParVendeur(@PathVariable Long vendeurId) {
        return ResponseEntity.ok(annonceService.listerParVendeur(vendeurId));
    }

    @Operation(summary = "Detail d'une annonce")
    @GetMapping("/{id}")
    public ResponseEntity<AnnonceResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(annonceService.getById(id));
    }

    @Operation(
            summary = "Modifier une annonce",
            description = "Change le nombre de parts a vendre et/ou le prix unitaire. Seul le vendeur peut modifier et uniquement tant que l'annonce est OUVERTE. Verifie la disponibilite des parts en tenant compte des autres annonces ouvertes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Annonce modifiee"),
            @ApiResponse(responseCode = "400", description = "Non vendeur, annonce non OUVERTE, ou parts insuffisantes"),
            @ApiResponse(responseCode = "404", description = "Annonce inconnue")
    })
    @PutMapping("/{id}")
    public ResponseEntity<AnnonceResponse> modifier(@PathVariable Long id, @Valid @RequestBody AnnonceUpdateRequest request) {
        return ResponseEntity.ok(annonceService.modifier(id, authInvestisseur.currentId(), request));
    }

    @Operation(
            summary = "Annuler une annonce",
            description = "Seul le vendeur (JWT) peut annuler, et uniquement si elle est OUVERTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Annonce annulee"),
            @ApiResponse(responseCode = "400", description = "Tentative par un autre investisseur ou annonce non OUVERTE"),
            @ApiResponse(responseCode = "404", description = "Annonce inconnue")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<AnnonceResponse> annuler(@PathVariable Long id) {
        return ResponseEntity.ok(annonceService.annuler(id, authInvestisseur.currentId()));
    }
}
