package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.DividendeResponse;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.repository.DividendeRepository;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.DividendeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dividendes")
@RequiredArgsConstructor
@Tag(name = "Dividendes", description = "Consultation des dividendes distribues + balance investisseur + marquage payout")
public class DividendeController {

    private final DividendeQueryService dividendeQuery;
    private final DividendeRepository dividendeRepository;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(summary = "Mes dividendes", description = "Dividendes percus par l'investisseur connecte.")
    @GetMapping("/me")
    public ResponseEntity<List<DividendeResponse>> mesDividendes() {
        return ResponseEntity.ok(dividendeQuery.listerPour(authInvestisseur.currentId()));
    }

    @Operation(
            summary = "Ma balance dividendes",
            description = """
                    Retourne :
                    - aRetirer : somme des dividendes en statut VALIDE (calcules mais pas encore verses)
                    - dejaRecu : somme des dividendes deja PAYE
                    - total : aRetirer + dejaRecu
                    - nbARetirer / nbDejaRecu : compteurs""")
    @GetMapping("/me/balance")
    public ResponseEntity<Map<String, Object>> maBalance() {
        return ResponseEntity.ok(dividendeQuery.balanceFor(authInvestisseur.currentId()));
    }

    @Operation(summary = "Dividendes d'un investisseur (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/investisseur/{investisseurId}")
    public ResponseEntity<List<DividendeResponse>> dividendesInvestisseur(@PathVariable Long investisseurId) {
        return ResponseEntity.ok(dividendeQuery.listerPour(investisseurId));
    }

    @Operation(summary = "Dividendes generes par un revenu", description = "Utile pour verifier une distribution apres coup.")
    @GetMapping("/revenu/{revenuId}")
    public ResponseEntity<List<DividendeResponse>> dividendesRevenu(@PathVariable Long revenuId) {
        return ResponseEntity.ok(dividendeQuery.listerPourRevenu(revenuId));
    }

    @Operation(summary = "Tous les dividendes (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<DividendeResponse>> tous() {
        return ResponseEntity.ok(dividendeQuery.listerTous());
    }

    @Operation(
            summary = "Marquer un dividende comme PAYE (admin)",
            description = """
                    L'admin a effectue le versement manuellement (Mobile Money, virement bancaire, crypto)
                    et confirme le payout. Set statut=PAYE + datePaiementEffectif=now.
                    Body : { methodePaiement: 'MOBILE_MONEY' | 'VIREMENT' | 'CRYPTO' | 'AUTRE', preuvePaiement: 'reference / lien' }""")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/marquer-paye")
    public ResponseEntity<DividendeResponse> marquerPaye(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Dividende d = dividendeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dividende introuvable : " + id));

        if (d.getStatut() == StatutPaiement.PAYE) {
            throw new IllegalStateException("Ce dividende est deja marque PAYE.");
        }
        if (d.getStatut() != StatutPaiement.VALIDE) {
            throw new IllegalStateException(
                "Seuls les dividendes VALIDE peuvent etre marques PAYE (actuel : " + d.getStatut() + ")");
        }

        String methode = body.getOrDefault("methodePaiement", "AUTRE");
        String preuve = body.getOrDefault("preuvePaiement", "");

        d.setStatut(StatutPaiement.PAYE);
        d.setDatePaiementEffectif(LocalDate.now());
        d.setMethodePaiement(methode);
        d.setPreuvePaiement(preuve.isBlank() ? null : preuve);
        dividendeRepository.save(d);

        // Reutilise le mapping du service (sans casser le contrat)
        var list = dividendeQuery.listerPourRevenu(d.getRevenus().getId());
        var saved = list.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
        return ResponseEntity.ok(saved);
    }
}
