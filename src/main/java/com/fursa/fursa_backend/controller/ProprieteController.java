package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.dto.ProprieteResponse;
import com.fursa.fursa_backend.mapper.ProprieteMapper;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.service.ProprieteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/proprietes")
@RequiredArgsConstructor
@Tag(name = "Proprietes & Fichiers", description = "CRUD des proprietes immobilieres et upload de documents (Imelda)")
public class ProprieteController {

    private final ProprieteService proprieteService;
    private final ProprieteMapper proprieteMapper;

    @Operation(
            summary = "Creer une propriete (admin)",
            description = "Cree une propriete avec ses metadonnees et optionnellement des fichiers (images/PDF). Requete multipart.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Propriete creee"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides")
    })
    @PostMapping(value = "/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> ajouter(
            @RequestPart("propriete") @Valid ProprieteRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        Propriete created = proprieteService.creerPropriete(request, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proprieteMapper.toResponse(created));
    }

    @Operation(summary = "Modifier une propriete (admin)")
    @PutMapping(value = "/admin/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> modifier(
            @PathVariable Long id,
            @RequestPart("propriete") @Valid ProprieteRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        Propriete updated = proprieteService.modifierPropriete(id, request, files);
        return ResponseEntity.ok(proprieteMapper.toResponse(updated));
    }

    @Operation(summary = "Lister les proprietes", description = "Retourne toutes les proprietes du catalogue.")
    @GetMapping("/public")
    public ResponseEntity<List<ProprieteResponse>> list() {
        List<ProprieteResponse> result = proprieteService.listerTout()
                .stream().map(proprieteMapper::toResponse).toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Detail d'une propriete")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trouvee"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue")
    })
    @GetMapping("/public/{id}")
    public ResponseEntity<ProprieteResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(
                proprieteMapper.toResponse(proprieteService.detail(id))
        );
    }

    @Operation(summary = "Supprimer une propriete (admin)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Supprimee"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue")
    })
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        proprieteService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}