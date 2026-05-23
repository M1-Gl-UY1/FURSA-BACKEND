package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.RefusRevenuRequest;
import com.fursa.fursa_backend.dto.RevenuRequest;
import com.fursa.fursa_backend.dto.RevenuResponse;
import com.fursa.fursa_backend.dto.StatutDeclarationResponse;
import com.fursa.fursa_backend.dto.SubmissionRevenuRequest;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.RevenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/revenus")
@RequiredArgsConstructor
@Tag(name = "Revenus", description = "CRUD des revenus d'exploitation (admin) + workflow déclaration propriétaire")
public class RevenuController {

    private final RevenuService revenuService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    // =========================================================================
    // Workflow historique (admin)
    // =========================================================================

    @Operation(summary = "Enregistrer un nouveau revenu (admin)",
            description = "Admin : declare un revenu percu pour une propriete. Statut = VALIDE direct (peut etre distribue immediatement).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Revenu cree"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<RevenuResponse> creer(@Valid @RequestBody RevenuRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(revenuService.creer(request));
    }

    @Operation(summary = "Lister tous les revenus")
    @GetMapping
    public ResponseEntity<List<RevenuResponse>> lister() {
        return ResponseEntity.ok(revenuService.lister());
    }

    @Operation(summary = "Revenus d'une propriete")
    @GetMapping("/propriete/{proprieteId}")
    public ResponseEntity<List<RevenuResponse>> listerParPropriete(@PathVariable Long proprieteId) {
        return ResponseEntity.ok(revenuService.listerParPropriete(proprieteId));
    }

    @Operation(summary = "Detail d'un revenu")
    @GetMapping("/{id}")
    public ResponseEntity<RevenuResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(revenuService.getById(id));
    }

    // =========================================================================
    // PHASE 8 : workflow déclaration propriétaire
    // =========================================================================

    @Operation(summary = "Soumettre une déclaration de revenu (propriétaire)",
            description = "Le propriétaire d'un bien soumet une déclaration de revenu en attente de validation admin. Statut auto = EN_REVIEW.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Soumission enregistrée"),
            @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas propriétaire du bien"),
            @ApiResponse(responseCode = "404", description = "Propriété introuvable")
    })
    @PostMapping("/submissions")
    public ResponseEntity<RevenuResponse> soumettre(@Valid @RequestBody SubmissionRevenuRequest request) {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.status(HttpStatus.CREATED).body(revenuService.soumettre(userId, request));
    }

    @Operation(summary = "Soumettre une déclaration avec justificatif PDF (propriétaire)",
            description = "Variante multipart : permet d'attacher directement le justificatif (PMS, relevé bancaire) à la soumission.")
    @PostMapping(value = "/submissions/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RevenuResponse> soumettreMultipart(
            @RequestParam Long proprieteId,
            @RequestParam BigDecimal montantTotal,
            @RequestParam(required = false) String periodeDebut,
            @RequestParam(required = false) String periodeFin,
            @RequestParam(required = false) MultipartFile justificatif) {
        Long userId = authInvestisseur.currentId();
        SubmissionRevenuRequest req = new SubmissionRevenuRequest(
                proprieteId,
                montantTotal,
                periodeDebut != null && !periodeDebut.isBlank() ? LocalDate.parse(periodeDebut) : null,
                periodeFin != null && !periodeFin.isBlank() ? LocalDate.parse(periodeFin) : null
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(revenuService.soumettreAvecJustificatif(userId, req, justificatif));
    }

    @Operation(summary = "Uploader / remplacer le justificatif d'une déclaration (propriétaire)",
            description = "Permet d'ajouter ou remplacer le PDF/image après soumission, tant que le revenu est EN_REVIEW ou REFUSE.")
    @PostMapping(value = "/{id}/justificatif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RevenuResponse> uploadJustificatif(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.ok(revenuService.uploadJustificatif(userId, id, file));
    }

    @Operation(summary = "Mes déclarations de revenu", description = "Liste les revenus que j'ai déclarés sur mes biens.")
    @GetMapping("/me")
    public ResponseEntity<List<RevenuResponse>> mesRevenus() {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.ok(revenuService.listerMesRevenus(userId));
    }

    @Operation(summary = "Approuver une déclaration de revenu (admin)", description = "Passe le statut de EN_REVIEW à VALIDE. Notifie le proposeur.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/approuver")
    public ResponseEntity<RevenuResponse> approuver(@PathVariable Long id) {
        return ResponseEntity.ok(revenuService.approuver(id));
    }

    @Operation(summary = "Refuser une déclaration de revenu (admin)", description = "Passe le statut à REFUSE avec motif. Notifie le proposeur.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/refuser")
    public ResponseEntity<RevenuResponse> refuser(
            @PathVariable Long id,
            @Valid @RequestBody RefusRevenuRequest request) {
        return ResponseEntity.ok(revenuService.refuser(id, request.motif()));
    }

    // =========================================================================
    // PHASE 10b : statut de declaration (window 1-5 + penalite retard)
    // =========================================================================

    @Operation(summary = "Statut de declaration mensuel d'une propriete",
            description = """
                    Indique si la propriete a deja ete declaree pour le mois N-1,
                    si on est dans la fenetre normale (1-5) ou en retard, et la penalite
                    applicable si declaration tardive.""")
    @GetMapping("/propriete/{proprieteId}/statut-mois-courant")
    public ResponseEntity<StatutDeclarationResponse> statutCourant(@PathVariable Long proprieteId) {
        return ResponseEntity.ok(revenuService.statutDeclarationCourant(proprieteId));
    }

    @Operation(summary = "Statuts de declaration de mes proprietes",
            description = "Liste les statuts de declaration mensuelle pour toutes mes proprietes proposees.")
    @GetMapping("/me/statuts")
    public ResponseEntity<List<StatutDeclarationResponse>> mesStatuts() {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.ok(revenuService.statutsPourProposeur(userId));
    }

    @Operation(summary = "Statuts de declaration de toutes les proprietes (admin)",
            description = "Vue globale des declarations mensuelles pour toutes les proprietes de la plateforme.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/statuts")
    public ResponseEntity<List<StatutDeclarationResponse>> tousLesStatuts() {
        return ResponseEntity.ok(revenuService.statutsTouteLaPlateforme());
    }

    @Operation(summary = "Marquer l'argent du revenu comme reçu par FURSA (admin)",
            description = """
                    Quand l'admin a vérifié le justificatif et confirmé que le propriétaire a bien
                    versé le net à FURSA, il coche cette case. Sans ce flag, la distribution
                    aux investisseurs est refusée par DistributionService.
                    Body : { argentRecu: true | false }""")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/argent-recu")
    public ResponseEntity<RevenuResponse> marquerArgentRecu(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean recu = body == null || !body.containsKey("argentRecu")
                ? true
                : Boolean.TRUE.equals(body.get("argentRecu"));
        return ResponseEntity.ok(revenuService.marquerArgentRecu(id, recu));
    }
}
