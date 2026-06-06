package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.HistoriquePrixPartResponse;
import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.dto.ProprieteResponse;
import com.fursa.fursa_backend.dto.BrouillonPatchRequest;
import com.fursa.fursa_backend.dto.RefusRequest;
import com.fursa.fursa_backend.dto.SubmissionRequest;
import com.fursa.fursa_backend.mapper.ProprieteMapper;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.BlockchainRpcClient;
import com.fursa.fursa_backend.service.PrixPartService;
import com.fursa.fursa_backend.service.ProprieteBrouillonService;
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
    private final ProprieteBrouillonService brouillonService;

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

    @Operation(summary = "Lister les proprietes publiques",
            description = "Retourne uniquement les proprietes PUBLIEE (validees + tokenisees). "
                    + "Les biens EN_REVIEW, ACCEPTEE, EN_TOKENISATION, REFUSEE ne sont pas exposes.")
    @GetMapping("/public")
    public ResponseEntity<List<ProprieteResponse>> list() {
        List<ProprieteResponse> result = proprieteService.listerPubliees()
                .stream().map(proprieteMapper::toResponse).toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Lister toutes les proprietes (admin)",
            description = "Retourne TOUS les statuts sauf BROUILLON (en cours de soumission par "
                    + "l'investisseur). Inclut EN_REVIEW, ACCEPTEE, EN_TOKENISATION, PUBLIEE, REFUSEE.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<List<ProprieteResponse>> listAdmin() {
        List<ProprieteResponse> result = proprieteService.listerTout().stream()
                .filter(p -> p.getStatut() != com.fursa.fursa_backend.model.enumeration.StatutPropriete.BROUILLON)
                .map(proprieteMapper::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Detail d'une propriete (admin)",
            description = "Accessible quelque soit le statut (sauf BROUILLON appartenant a un autre user).")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/{id}")
    public ResponseEntity<ProprieteResponse> detailAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(proprieteMapper.toResponse(proprieteService.detail(id)));
    }

    @Operation(summary = "Detail d'une propriete publique",
            description = "Accessible uniquement si statut = PUBLIEE. Sinon 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trouvee et publiee"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue ou non publiee")
    })
    @GetMapping("/public/{id}")
    public ResponseEntity<ProprieteResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(
                proprieteMapper.toResponse(proprieteService.detailPublic(id))
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
    // PHASE 9 (02/06/2026) : wizard auto-save brouillon
    // L'investisseur cree un brouillon, le complete progressivement via PATCH +
    // upload medias, puis finalise pour passer en EN_REVIEW. Reprise depuis tout
    // appareil. Cf ProprieteBrouillonService pour le detail des validations.
    // =========================================================================

    @Operation(summary = "Creer un brouillon vide",
            description = "Renvoie un id pour pouvoir PATCH les etapes suivantes du wizard.")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping("/brouillon")
    public ResponseEntity<ProprieteResponse> creerBrouillon() {
        Long userId = authInvestisseur.currentId();
        Propriete brouillon = brouillonService.creerBrouillon(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proprieteMapper.toResponse(brouillon));
    }

    @Operation(summary = "Mettre a jour partiellement un brouillon",
            description = "Tous les champs sont optionnels. Seuls les champs non-null sont appliques.")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PatchMapping("/brouillon/{id}")
    public ResponseEntity<ProprieteResponse> patcherBrouillon(
            @PathVariable Long id,
            @RequestBody BrouillonPatchRequest req) {
        Long userId = authInvestisseur.currentId();
        Propriete updated = brouillonService.patcher(id, userId, req);
        return ResponseEntity.ok(proprieteMapper.toResponse(updated));
    }

    @Operation(summary = "Ajouter des photos a un brouillon",
            description = "Upload multipart. Chaque photo a sa section (FACADE, SALON, ...) en parallele.")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping(value = "/brouillon/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> ajouterPhotosBrouillon(
            @PathVariable Long id,
            @RequestPart("photos") List<MultipartFile> photos,
            @RequestParam(value = "sections", required = false) List<String> sections) {
        Long userId = authInvestisseur.currentId();
        Propriete updated = brouillonService.ajouterPhotos(id, userId, photos, sections);
        return ResponseEntity.ok(proprieteMapper.toResponse(updated));
    }

    @Operation(summary = "Definir la video de visite d'un brouillon",
            description = "Remplace l'eventuelle video precedente.")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping(value = "/brouillon/{id}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> setVideoBrouillon(
            @PathVariable Long id,
            @RequestPart("video") MultipartFile video) {
        Long userId = authInvestisseur.currentId();
        Propriete updated = brouillonService.setVideo(id, userId, video);
        return ResponseEntity.ok(proprieteMapper.toResponse(updated));
    }

    @Operation(summary = "Ajouter des documents legaux a un brouillon",
            description = "Upload multipart. Categories parallele en RequestParam : TITRE_FONCIER, PERMIS_CONSTRUIRE, ...")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping(value = "/brouillon/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> ajouterDocsBrouillon(
            @PathVariable Long id,
            @RequestPart("documents") List<MultipartFile> documents,
            @RequestParam(value = "categories", required = false) List<String> categories) {
        Long userId = authInvestisseur.currentId();
        Propriete updated = brouillonService.ajouterDocuments(id, userId, documents, categories);
        return ResponseEntity.ok(proprieteMapper.toResponse(updated));
    }

    @Operation(summary = "Supprimer un media (photo, video ou document) d'un brouillon")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @DeleteMapping("/brouillon/{id}/medias/{mediaId}")
    public ResponseEntity<Void> supprimerMediaBrouillon(
            @PathVariable Long id,
            @PathVariable Long mediaId) {
        Long userId = authInvestisseur.currentId();
        brouillonService.supprimerMedia(id, userId, mediaId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Finaliser le brouillon : bascule BROUILLON -> EN_REVIEW",
            description = "Valide toutes les regles metier (champs obligatoires, photos requises, "
                    + "documents legaux conditionnels). Si invalide, le brouillon reste BROUILLON.")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PostMapping("/brouillon/{id}/finaliser")
    public ResponseEntity<ProprieteResponse> finaliserBrouillon(@PathVariable Long id) {
        Long userId = authInvestisseur.currentId();
        Propriete soumise = brouillonService.finaliser(id, userId);
        return ResponseEntity.ok(proprieteMapper.toResponse(soumise));
    }

    @Operation(summary = "Supprimer un brouillon (ne fonctionne que tant qu'il est BROUILLON)")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @DeleteMapping("/brouillon/{id}")
    public ResponseEntity<Void> supprimerBrouillon(@PathVariable Long id) {
        Long userId = authInvestisseur.currentId();
        brouillonService.supprimerBrouillon(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lister mes brouillons en cours")
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @GetMapping("/brouillon/me")
    public ResponseEntity<List<ProprieteResponse>> mesBrouillons() {
        Long userId = authInvestisseur.currentId();
        List<ProprieteResponse> brouillons = brouillonService.listerBrouillons(userId)
                .stream().map(proprieteMapper::toResponse).toList();
        return ResponseEntity.ok(brouillons);
    }

    // =========================================================================
    // PHASE 7 : workflow soumission propriétaire (legacy en 1 requete multipart)
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
            // P1 : documents legaux (PDFs). Categorisation introduite le 02/06/2026.
            @RequestPart(value = "documents", required = false) List<MultipartFile> documents,
            // 02/06/2026 : categorie pour chaque document (parallele a documents, meme ordre).
            // Valeurs : TITRE_FONCIER, PERMIS_CONSTRUIRE, CONTRAT_GESTION, CONTRAT_BAIL, RELEVE_AIRBNB, AUTRE.
            @org.springframework.web.bind.annotation.RequestParam(value = "documentCategories", required = false) List<String> documentCategories) {

        Long userId = authInvestisseur.currentId();
        Propriete created = proprieteService.soumettre(
                userId, request, files, video, photos, photoSections, documents, documentCategories);
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

    @Operation(summary = "Modifier ma propriete (proposeur)",
            description = "Modification partielle (PATCH) avec controle d'acces selon le statut : "
                    + "EN_REVIEW/ACCEPTEE -> tous champs sauf prix/parts/devise ; "
                    + "EN_TOKENISATION/PUBLIEE -> uniquement nom + description ; "
                    + "REFUSEE -> bloque (faut re-soumettre) ; "
                    + "BROUILLON -> utiliser /brouillon/{id}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Modifie"),
            @ApiResponse(responseCode = "400", description = "Champ non autorise pour ce statut"),
            @ApiResponse(responseCode = "403", description = "Vous n'etes pas le proposeur"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue"),
            @ApiResponse(responseCode = "409", description = "Statut bloque la modification (REFUSEE)")
    })
    @PreAuthorize("hasRole('INVESTISSEUR')")
    @PatchMapping("/me/{id}")
    public ResponseEntity<ProprieteResponse> modifierMaPropriete(
            @PathVariable Long id,
            @RequestBody BrouillonPatchRequest req) {
        Long userId = authInvestisseur.currentId();
        Propriete updated = proprieteService.modifierParProposeur(id, userId, req);
        return ResponseEntity.ok(proprieteMapper.toResponse(updated));
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

    @Operation(
            summary = "Valider une propriete : approuve + tokenise + publie",
            description = "Endpoint unifie cree le 02/06/2026. En 1 clic admin : approuve, "
                    + "broadcast la tx blockchain, et passe le bien en EN_TOKENISATION. "
                    + "Le worker TokenisationWorker bascule ensuite automatiquement en "
                    + "PUBLIEE quand la tx est minee sur Sepolia (~15-60s).")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Validation acceptee, tokenisation en cours"),
            @ApiResponse(responseCode = "400", description = "Statut incompatible"),
            @ApiResponse(responseCode = "404", description = "Propriete inconnue"),
            @ApiResponse(responseCode = "500", description = "Erreur RPC blockchain")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/{id}/valider")
    public ResponseEntity<ProprieteResponse> valider(@PathVariable Long id) throws Exception {
        Propriete validee = proprieteService.validerEtTokeniser(id, tokenisationService);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(proprieteMapper.toResponse(validee));
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
    // V2 G.7 (05/06/2026) : Phase E medias post-tokenisation
    // =========================================================================

    @Operation(summary = "Ajouter des photos a un bien deja accepte / tokenise / publie (proprio)",
            description = """
                    Permet au proposeur d'enrichir son bien apres la validation initiale.
                    Autorise uniquement si le bien est ACCEPTEE, EN_TOKENISATION ou PUBLIEE.
                    Refuse pour EN_REVIEW (utiliser le wizard brouillon) et REFUSEE.

                    Chaque photo est associee a une section (FACADE, SALON, TERRASSE, ...).
                    Les codes admin-configurables sont supportes (V2 G.4).""")
    @PostMapping(value = "/{id}/medias/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProprieteResponse> ajouterPhotosPostTokenisation(
            @PathVariable Long id,
            @RequestPart("photos") List<MultipartFile> photos,
            @RequestPart(value = "sections", required = false) List<String> sections) {
        Long userId = authInvestisseur.currentId();
        Propriete p = proprieteService.ajouterPhotosPostTokenisation(userId, id, photos, sections);
        return ResponseEntity.ok(proprieteMapper.toResponse(p));
    }

    // =========================================================================
    // Phase Certification (Hugh 22/05/2026) : etape post-creation separee
    // =========================================================================

    // V2 I (06/06/2026) : phase Certification supprimee. Les endpoints
    // /certification/* ont ete retires car redondants avec la validation
    // admin du wizard (EN_REVIEW -> ACCEPTEE). Voir DEPRECATIONS.md.

    // =========================================================================
    // P4 (Hugh 22/05/2026) : modele FURSA acheteur
    // =========================================================================

    @Operation(summary = "Toggle le flag 'Acquis FURSA' (admin)",
            description = """
                    Marque ou de-marque un bien comme acquis par FURSA en one-time
                    aupres d'un promoteur (workflow Paje Square). Body :
                    { "acquisFursa": true | false }. Le bien affiche un badge "Acquis FURSA"
                    cote investisseur si true.""")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/{id}/acquis-fursa")
    public ResponseEntity<ProprieteResponse> toggleAcquisFursa(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean flag = body == null ? null : body.get("acquisFursa");
        if (flag == null) {
            return ResponseEntity.badRequest().build();
        }
        Propriete p = proprieteService.setAcquisFursa(id, flag);
        return ResponseEntity.ok(proprieteMapper.toResponse(p));
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
