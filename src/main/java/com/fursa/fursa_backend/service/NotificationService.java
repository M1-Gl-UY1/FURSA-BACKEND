package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.NotificationResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Notification;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification envoyer(Investisseur destinataire, String titre, String message, TypeMessage type) {
        Notification n = new Notification();
        n.setDestinataire(destinataire);
        n.setTitre(titre);
        n.setMessage(message);
        n.setType(type);
        n.setDate(LocalDateTime.now());
        n.setLu(false);
        return notificationRepository.save(n);
    }

    public List<NotificationResponse> listerPour(Long investisseurId) {
        return notificationRepository.findByDestinataireIdOrderByDateDesc(investisseurId)
                .stream().map(this::toResponse).toList();
    }

    public List<NotificationResponse> listerNonLues(Long investisseurId) {
        return notificationRepository.findByDestinataireIdAndLuFalseOrderByDateDesc(investisseurId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public NotificationResponse marquerLue(Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification non trouvee: id=" + notificationId));
        n.setLu(true);
        return toResponse(notificationRepository.save(n));
    }

    @Transactional
    public int marquerToutLu(Long investisseurId) {
        var nonLues = notificationRepository.findByDestinataireIdAndLuFalseOrderByDateDesc(investisseurId);
        nonLues.forEach(n -> n.setLu(true));
        notificationRepository.saveAll(nonLues);
        return nonLues.size();
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitre(), n.getMessage(), n.getType(), n.getDate(), n.getLu());
    }
}
