package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.ListeAttenteRequest;
import com.fursa.fursa_backend.dto.ListeAttenteResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.ListeAttenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P2 (Hugh 22/05/2026) : endpoints publics et utilisateur pour la liste d'attente
 * sur les biens entierement vendus.
 *
 * Voir PRIX_DYNAMIQUE_FURSA.md §4 pour le lien avec le prix dynamique.
 */
@RestController
@RequestMapping("/api/liste-attente")
@RequiredArgsConstructor
@Tag(name = "Liste d'attente", description = "Inscription en file d'attente pour un bien entierement vendu")
public class ListeAttenteController {

    private final ListeAttenteService service;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(summary = "M'inscrire en liste d'attente",
            description = "Inscrit l'utilisateur authentifie en liste d'attente pour un bien entierement vendu. " +
                    "Le bien doit etre PUBLIEE et avoir 0 parts disponibles a l'achat normal.")
    @PostMapping
    public ResponseEntity<ListeAttenteResponse> inscrire(@Valid @RequestBody ListeAttenteRequest req) {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.inscrire(userId, req));
    }

    @Operation(summary = "Annuler mon inscription",
            description = "Retire l'utilisateur de la file d'attente. Seul le titulaire peut annuler sa propre inscription.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ListeAttenteResponse> desinscrire(@PathVariable Long id) {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.ok(service.desinscrire(id, userId));
    }

    @Operation(summary = "Mes inscriptions",
            description = "Toutes mes inscriptions (EN_ATTENTE, NOTIFIE, SERVI, ANNULE) du plus recent au plus ancien.")
    @GetMapping("/me")
    public ResponseEntity<List<ListeAttenteResponse>> mesInscriptions() {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.ok(service.mesInscriptions(userId));
    }

    @Operation(summary = "File d'attente d'un bien",
            description = "Liste publique des inscriptions EN_ATTENTE sur un bien (ordre FIFO). " +
                    "Permet d'evaluer la pression avant de s'inscrire soi-meme.")
    @GetMapping("/propriete/{proprieteId}")
    public ResponseEntity<List<ListeAttenteResponse>> filePropriete(@PathVariable Long proprieteId) {
        return ResponseEntity.ok(service.filePropriete(proprieteId));
    }
}
