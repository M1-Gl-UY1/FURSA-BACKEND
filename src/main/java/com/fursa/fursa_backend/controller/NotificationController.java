package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.NotificationResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Consulter et marquer comme lues les notifications d'un investisseur")
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
        return ResponseEntity.ok(notificationService.marquerLue(id));
    }

    @Operation(summary = "Marquer toutes mes notifications comme lues", description = "Retourne le nombre de notifications modifiees.")
    @PutMapping("/me/lu-tout")
    public ResponseEntity<java.util.Map<String, Integer>> marquerToutLu() {
        int n = notificationService.marquerToutLu(authInvestisseur.currentId());
        return ResponseEntity.ok(java.util.Map.of("marquees", n));
    }
}
