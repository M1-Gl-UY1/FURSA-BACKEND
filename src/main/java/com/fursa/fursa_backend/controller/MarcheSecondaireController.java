package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.AchatAnnonceRequest;
import com.fursa.fursa_backend.dto.AchatAnnonceResponse;
import com.fursa.fursa_backend.service.AnnonceService;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marche-secondaire")
@RequiredArgsConstructor
@Tag(name = "Marche secondaire", description = "Achat entre investisseurs d'une annonce existante (Mimche)")
public class MarcheSecondaireController {

    private final AnnonceService annonceService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(
            summary = "Acheter des parts d'une annonce",
            description = """
                    Logique critique :
                    - verifie que l'annonce est OUVERTE, que l'acheteur (JWT) != vendeur et qu'il reste assez de parts
                    - transfere la possession (supprime celle du vendeur si elle atteint 0, cree celle de l'acheteur si elle n'existe pas)
                    - cree un Paiement (VALIDE) + une Transaction (SUCCES, hash UUID)
                    - decremente l'annonce et la passe en COMPLETEE quand elle est epuisee
                    - envoie deux notifications (vendeur + acheteur, type TRANSACTION)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Achat reussi"),
            @ApiResponse(responseCode = "400", description = "Annonce non OUVERTE, parts insuffisantes, achat de sa propre annonce"),
            @ApiResponse(responseCode = "404", description = "Annonce ou acheteur inconnu")
    })
    @PostMapping("/annonces/{annonceId}/acheter")
    public ResponseEntity<AchatAnnonceResponse> acheter(
            @PathVariable Long annonceId,
            @Valid @RequestBody AchatAnnonceRequest request) {
        return ResponseEntity.ok(
                annonceService.acheter(annonceId, authInvestisseur.currentId(), request));
    }
}
