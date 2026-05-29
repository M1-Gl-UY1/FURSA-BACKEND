package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.NotificationResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Notification;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.NotificationRepository;
import com.fursa.fursa_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // Envoi cible : a UN destinataire precis
    // =========================================================================

    /** Overload sans lien (compat ascendante avec l'ancienne signature). */
    @Transactional
    public Notification envoyer(Investisseur destinataire, String titre, String message, TypeMessage type) {
        return envoyer(destinataire, titre, message, type, null);
    }

    @Transactional
    public Notification envoyer(Investisseur destinataire, String titre, String message,
                                  TypeMessage type, String lien) {
        Notification n = new Notification();
        n.setDestinataire(destinataire);
        n.setTitre(titre);
        n.setMessage(message);
        n.setType(type);
        n.setDate(LocalDateTime.now());
        n.setLu(false);
        n.setLien(lien);
        return notificationRepository.save(n);
    }

    // =========================================================================
    // Broadcasts : a TOUS les investisseurs (hors admins, hors expediteur)
    // =========================================================================

    /**
     * Envoie la meme notif a tous les comptes role INVESTISSEUR (pas aux admins),
     * en excluant optionnellement un user (ex : l'acheteur ne se notifie pas lui-meme).
     */
    @Transactional
    public int broadcastInvestisseurs(String titre, String message, TypeMessage type,
                                       String lien, Long excludeUserId) {
        List<Notification> batch = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.INVESTISSEUR)
                .filter(u -> excludeUserId == null || !excludeUserId.equals(u.getId()))
                .filter(u -> u instanceof Investisseur)
                .map(u -> {
                    Notification n = new Notification();
                    n.setDestinataire((Investisseur) u);
                    n.setTitre(titre);
                    n.setMessage(message);
                    n.setType(type);
                    n.setDate(LocalDateTime.now());
                    n.setLu(false);
                    n.setLien(lien);
                    return n;
                })
                .toList();
        if (batch.isEmpty()) return 0;
        notificationRepository.saveAll(batch);
        log.info("[Notif] Broadcast '{}' -> {} investisseur(s)", titre, batch.size());
        return batch.size();
    }

    // =========================================================================
    // Consultation
    // =========================================================================

    public List<NotificationResponse> listerPour(Long investisseurId) {
        return notificationRepository.findByDestinataireIdOrderByDateDesc(investisseurId)
                .stream().map(this::toResponse).toList();
    }

    public List<NotificationResponse> listerNonLues(Long investisseurId) {
        return notificationRepository.findByDestinataireIdAndLuFalseOrderByDateDesc(investisseurId)
                .stream().map(this::toResponse).toList();
    }

    // =========================================================================
    // Mutations (avec ownership check)
    // =========================================================================

    @Transactional
    public NotificationResponse marquerLue(Long notificationId, Long callerInvestisseurId) {
        Notification n = findOwned(notificationId, callerInvestisseurId);
        n.setLu(true);
        return toResponse(notificationRepository.save(n));
    }

    @Transactional
    public NotificationResponse marquerNonLue(Long notificationId, Long callerInvestisseurId) {
        Notification n = findOwned(notificationId, callerInvestisseurId);
        n.setLu(false);
        return toResponse(notificationRepository.save(n));
    }

    @Transactional
    public int marquerToutLu(Long investisseurId) {
        var nonLues = notificationRepository.findByDestinataireIdAndLuFalseOrderByDateDesc(investisseurId);
        nonLues.forEach(n -> n.setLu(true));
        notificationRepository.saveAll(nonLues);
        return nonLues.size();
    }

    @Transactional
    public void supprimer(Long notificationId, Long callerInvestisseurId) {
        Notification n = findOwned(notificationId, callerInvestisseurId);
        notificationRepository.delete(n);
    }

    @Transactional
    public int supprimerToutLu(Long investisseurId) {
        var lues = notificationRepository.findByDestinataireIdOrderByDateDesc(investisseurId)
                .stream().filter(n -> Boolean.TRUE.equals(n.getLu())).toList();
        notificationRepository.deleteAll(lues);
        return lues.size();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Notification findOwned(Long notificationId, Long callerInvestisseurId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Notification non trouvee : id=" + notificationId));
        if (n.getDestinataire() == null
                || !n.getDestinataire().getId().equals(callerInvestisseurId)) {
            throw new AccessDeniedException(
                    "Cette notification ne vous appartient pas.");
        }
        return n;
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getTitre(), n.getMessage(), n.getType(),
                n.getDate(), n.getLu(), n.getLien());
    }
}
