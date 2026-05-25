package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.HistoriquePrixPartResponse;
import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.dto.ProprieteResponse;
import com.fursa.fursa_backend.dto.RefusRequest;
import com.fursa.fursa_backend.dto.SubmissionRequest;
import com.fursa.fursa_backend.mapper.ProprieteMapper;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.BlockchainRpcClient;
import com.fursa.fursa_backend.service.PrixPartService;
import com.fursa.fursa_backend.service.ProprieteService;
import com.fursa.fursa_backend.service.TokenisationService;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proprietes")
@RequiredArgsConstructor
@Tag(name = "Proprietes & Fichiers", description = "CRUD des proprietes immobilieres et upload de documents")
public class ProprieteController {

    private final ProprieteService proprieteService;
    private final ProprieteMapper proprieteMapper;
    private final AuthenticatedInvestisseurService authInvestisseur;
    private final BlockchainRpcClient blockchainRpcClient;
    private final TokenisationService tokenisationService;
    private final PrixPartService prixPartService;

    // =========================================================================
    // Création directe par admin (workflow historique)
    // =========================================================================

    @Operation(
            summary = "Creer une propriete (admin)",
            description = "Cree une propriete avec ses metadonnees et optionnellement des fichiers (images/PDF). Requete multipart.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Propriete creee"),
            @ApiResponse(responseCode = "400", description = "Donnees invalides")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/admin", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> ajouter(
            @RequestPart("propriete") @Valid ProprieteRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        Propriete created = proprieteService.creerPropriete(request, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proprieteMapper.toResponse(created));
    }

    @Operation(summary = "Modifier une propriete (admin)")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        proprieteService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Publier une propriete (admin)",
            description = "Passe le statut a PUBLIEE. Idempotent.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/publier")
    public ResponseEntity<ProprieteResponse> publier(@PathVariable Long id) {
        return ResponseEntity.ok(proprieteMapper.toResponse(proprieteService.publier(id)));
    }

    @Operation(
            summary = "Progression du financement",
            description = "Retourne parts totales, vendues, disponibles et le pourcentage vendu (0-100).")
    @GetMapping("/public/{id}/progression")
    public ResponseEntity<com.fursa.fursa_backend.dto.ProgressionResponse> progression(@PathVariable Long id) {
        return ResponseEntity.ok(proprieteService.progression(id));
    }

    // =========================================================================
    // PHASE 7 : workflow soumission propriétaire
    // =========================================================================

    @Operation(
            summary = "Soumettre un bien (investisseur)",
            description = "Soumet un bien immobilier pour validation par l'admin. Statut auto = EN_REVIEW. proposeurId = utilisateur courant. Notifie tous les admins.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Soumission enregistrée"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    // Bloque les comptes ADMIN : ils créent les biens via /admin (pas via soumission).
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> soumettre(
            @RequestPart("submission") @Valid SubmissionRequest request,
            // Legacy : ancien champ "files" (toutes photos vrac, sans section). Conserve pour compat.
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            // P1 (Hugh 22/05/2026) : video de visite guidee (1 fichier max).
            @RequestPart(value = "video", required = false) MultipartFile video,
            // P1 : photos structurees par section (FACADE, SALON, ...).
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            // P1 : sections paralleles aux photos (meme ordre). Ex : ["FACADE", "SALON", "CHAMBRE"]
            @org.springframework.web.bind.annotation.RequestParam(value = "photoSections", required = false) List<String> photoSections,
            // P1 : documents legaux (PDFs). Pour MVP gardes ensemble, certification separee plus tard.
            @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {

        Long userId = authInvestisseur.currentId();
        Propriete created = proprieteService.soumettre(
                userId, request, files, video, photos, photoSections, documents);
        return ResponseEntity.status(HttpStatus.CREATED).body(proprieteMapper.toResponse(created));
    }

    @Operation(summary = "Mes propriétés proposées", description = "Liste les biens soumis par l'utilisateur courant (tous statuts).")
    @GetMapping("/me")
    public ResponseEntity<List<ProprieteResponse>> mesProprietesProposees() {
        Long userId = authInvestisseur.currentId();
        return ResponseEntity.ok(
                proprieteService.listerProposeesPar(userId).stream()
                        .map(proprieteMapper::toResponse).toList()
        );
    }

    @Operation(summary = "Détail d'une propriété proposée par moi", description = "Accessible au proposeur ou à un admin.")
    @PreAuthorize("hasRole('ADMIN') or @proprieteSecurity.isProposeur(#id, principal.id)")
    @GetMapping("/me/{id}")
    public ResponseEntity<ProprieteResponse> detailMaPropriete(@PathVariable Long id) {
        return ResponseEntity.ok(proprieteMapper.toResponse(proprieteService.detail(id)));
    }

    @Operation(summary = "Approuver une propriété (admin)", description = "Passe le statut de EN_REVIEW à ACCEPTEE. Notifie le proposeur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approuvée"),
            @ApiResponse(responseCode = "400", description = "Statut incompatible"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/approuver")
    public ResponseEntity<ProprieteResponse> approuver(@PathVariable Long id) {
        return ResponseEntity.ok(proprieteMapper.toResponse(proprieteService.approuver(id)));
    }

    @Operation(summary = "Refuser une propriété (admin)", description = "Passe le statut à REFUSEE avec motif. Notifie le proposeur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refusée"),
            @ApiResponse(responseCode = "400", description = "Statut incompatible ou motif manquant"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/refuser")
    public ResponseEntity<ProprieteResponse> refuser(
            @PathVariable Long id,
            @Valid @RequestBody RefusRequest request) {
        return ResponseEntity.ok(proprieteMapper.toResponse(proprieteService.refuser(id, request.getMotif())));
    }

    // =========================================================================
    // Blockchain : statut RPC + tokenisation propriete
    // =========================================================================

    @Operation(summary = "Statut de la blockchain (connexion + parts disponibles on-chain)")
    @GetMapping("/public/blockchain/status")
    public ResponseEntity<Map<String, Object>> blockchainStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connecte", blockchainRpcClient.estConnecte());
        status.put("partsDisponibles", blockchainRpcClient.getPartsDisponibles());
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Tokeniser une propriete (admin)", description = "Deploie le smart contract ERC-20 pour cette propriete.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/tokeniser")
    public ResponseEntity<ProprieteResponse> tokeniser(@PathVariable Long id) throws Exception {
        Propriete tokenisee = tokenisationService.tokeniserPropriete(id);
        return ResponseEntity.ok(proprieteMapper.toResponse(tokenisee));
    }

    // =========================================================================
    // Phase Certification (Hugh 22/05/2026) : etape post-creation separee
    // =========================================================================

    @Operation(summary = "Uploader des documents legaux pour certification (proprio)",
            description = """
                    Permet au proprietaire d'uploader les documents legaux (titre foncier,
                    contrat, etc.) en vue de la certification. Les fichiers sont stockes
                    comme documents PDFs separes des photos. Acceptable a tout moment,
                    mais doit etre fait AVANT le clic 'Soumettre certification'.""")
    @PostMapping(value = "/{id}/certification/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> uploadDocsCertif(
            @PathVariable Long id,
            @RequestPart("documents") List<MultipartFile> documents) {
        Long userId = authInvestisseur.currentId();
        Propriete p = proprieteService.uploadDocumentCertification(userId, id, documents);
        return ResponseEntity.ok(proprieteMapper.toResponse(p));
    }

    @Operation(summary = "Soumettre la demande de certification (proprio)",
            description = """
                    Le proprietaire declare avoir uploade tous les documents necessaires
                    et demande la verification admin. Statut passe NON_CERTIFIE -> EN_REVIEW.
                    Pre-requis : au moins un document legal PDF uploade.""")
    @PostMapping("/{id}/certification/soumettre")
    public ResponseEntity<ProprieteResponse> soumettreCertif(@PathVariable Long id) {
        Long userId = authInvestisseur.currentId();
        Propriete p = proprieteService.soumettreCertification(userId, id);
        return ResponseEntity.ok(proprieteMapper.toResponse(p));
    }

    @Operation(summary = "Approuver la certification (admin)",
            description = "Le bien devient CERTIFIE et donc ACHETABLE par les investisseurs.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/certification/approuver")
    public ResponseEntity<ProprieteResponse> approuverCertif(@PathVariable Long id) {
        return ResponseEntity.ok(proprieteMapper.toResponse(proprieteService.approuverCertification(id)));
    }

    @Operation(summary = "Refuser la certification (admin)",
            description = "Body : { motif: 'min 10 caracteres' }. Le proprio peut re-uploader et resoumettre.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/certification/refuser")
    public ResponseEntity<ProprieteResponse> refuserCertif(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String motif = body == null ? null : body.get("motif");
        return ResponseEntity.ok(proprieteMapper.toResponse(proprieteService.refuserCertification(id, motif)));
    }

    @Operation(summary = "Liste des biens en attente de certification (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/certification/pending")
    public ResponseEntity<List<ProprieteResponse>> certifsEnAttente() {
        return ResponseEntity.ok(
                proprieteService.listerEnAttenteCertification().stream()
                        .map(proprieteMapper::toResponse).toList());
    }

    // =========================================================================
    // P1 (Hugh 22/05/2026) : prix dynamique
    // Voir PRIX_DYNAMIQUE_FURSA.md a la racine du projet.
    // =========================================================================

    @Operation(
            summary = "Historique des prix d'une part",
            description = "Snapshots chronologiques (du plus ancien au plus recent) des variations du prix unitaire d'une part. Alimente la sparkline cote investisseur.")
    @GetMapping("/{id}/historique-prix")
    public ResponseEntity<List<HistoriquePrixPartResponse>> historiquePrix(@PathVariable Long id) {
        return ResponseEntity.ok(prixPartService.historique(id));
    }
}
