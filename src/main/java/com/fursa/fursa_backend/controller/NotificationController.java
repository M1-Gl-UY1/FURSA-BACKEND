package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.NotificationResponse;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
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
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthenticatedInvestisseurService authInvestisseur;

    @GetMapping("/me")
    public ResponseEntity<List<NotificationResponse>> mesNotifications(
            @RequestParam(defaultValue = "false") boolean nonLuesSeulement) {
        Long id = authInvestisseur.currentId();
        return ResponseEntity.ok(
                nonLuesSeulement
                        ? notificationService.listerNonLues(id)
                        : notificationService.listerPour(id));
    }

    @GetMapping("/investisseur/{investisseurId}")
    public ResponseEntity<List<NotificationResponse>> lister(
            @PathVariable Long investisseurId,
            @RequestParam(defaultValue = "false") boolean nonLuesSeulement) {
        return ResponseEntity.ok(
                nonLuesSeulement
                        ? notificationService.listerNonLues(investisseurId)
                        : notificationService.listerPour(investisseurId));
    }

    @PutMapping("/{id}/lu")
    public ResponseEntity<NotificationResponse> marquerLue(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.marquerLue(id));
    }
}
