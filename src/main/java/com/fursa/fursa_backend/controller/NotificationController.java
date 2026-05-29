package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.NotificationResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Centre de notifications utilisateur (CRUD + broadcasts)")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @Operation(
            summary = "Mes notifications",
            description = "Liste les notifications de l'investisseur connecte, triees par date decroissante.")
    @GetMapping("/me")
    public ResponseEntity<List<NotificationResponse>> mesNotifications(
            @Parameter(description = "Ne retourner que les notifications non lues")
            @RequestParam(defaultValue = "false") boolean nonLuesSeulement) {
        Long id = authInvestisseur.currentId();
        return ResponseEntity.ok(
                nonLuesSeulement
                        ? notificationService.listerNonLues(id)
                        : notificationService.listerPour(id));
    }

    @Operation(summary = "Notifications d'un investisseur (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/investisseur/{investisseurId}")
    public ResponseEntity<List<NotificationResponse>> lister(
            @PathVariable Long investisseurId,
            @Parameter(description = "Ne retourner que les notifications non lues")
            @RequestParam(defaultValue = "false") boolean nonLuesSeulement) {
        return ResponseEntity.ok(
                nonLuesSeulement
                        ? notificationService.listerNonLues(investisseurId)
                        : notificationService.listerPour(investisseurId));
    }

    @Operation(summary = "Marquer une notification comme lue")
    @PutMapping("/{id}/lu")
    public ResponseEntity<NotificationResponse> marquerLue(@PathVariable Long id) {
        return ResponseEntity.ok(
                notificationService.marquerLue(id, authInvestisseur.currentId()));
    }

    @Operation(summary = "Marquer une notification comme non lue",
            description = "Permet a l'utilisateur de la remettre en avant dans son centre.")
    @PutMapping("/{id}/non-lu")
    public ResponseEntity<NotificationResponse> marquerNonLue(@PathVariable Long id) {
        return ResponseEntity.ok(
                notificationService.marquerNonLue(id, authInvestisseur.currentId()));
    }

    @Operation(summary = "Marquer toutes mes notifications comme lues",
            description = "Retourne le nombre de notifications modifiees.")
    @PutMapping("/me/lu-tout")
    public ResponseEntity<Map<String, Integer>> marquerToutLu() {
        int n = notificationService.marquerToutLu(authInvestisseur.currentId());
        return ResponseEntity.ok(Map.of("marquees", n));
    }

    @Operation(summary = "Supprimer une notification (ownership check)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        notificationService.supprimer(id, authInvestisseur.currentId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Supprimer toutes mes notifications deja lues",
            description = "Garde les non lues. Retourne le nombre de notifications supprimees.")
    @DeleteMapping("/me/lues")
    public ResponseEntity<Map<String, Integer>> supprimerToutLu() {
        int n = notificationService.supprimerToutLu(authInvestisseur.currentId());
        return ResponseEntity.ok(Map.of("supprimees", n));
    }
}
