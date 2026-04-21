package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.feature.observer.Observer;
import com.fursa.fursa_backend.feature.observer.Subject;
import com.fursa.fursa_backend.model.Notification;
import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import com.fursa.fursa_backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService implements Subject {

    private final NotificationRepository notificationRepository;
    private final InvestisseurRepository investisseurRepository;

    private final List<Observer> observers = new ArrayList<>();

    // ===================== OBSERVER =====================

    @Override
    public void attach(Observer observer) {
        observers.add(observer);
    }

    @Override
    public void detach(Observer observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(String event, Object data) {
        for (Observer observer : observers) {
            observer.update(event, data);
        }
    }

    // Méthode avec Long userId
    public Notification createNotification(Long userId, String title, String message, String type) {
        Investisseur investisseur = investisseurRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Investisseur non trouvé: " + userId));

        Notification notification = new Notification();
        notification.setInvestisseur(investisseur);
        notification.setTitre(title);
        notification.setMessage(message);
        notification.setType(TypeMessage.valueOf(type));
        notification.setDate(LocalDateTime.now());
        notification.setStatut(false);

        return notificationRepository.save(notification);
    }

    // Méthode avec User
    public Notification createNotification(User user, String title, String message, String type) {
        Investisseur investisseur = investisseurRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Investisseur non trouvé pour user: " + user.getId()));

        Notification notification = new Notification();
        notification.setInvestisseur(investisseur);
        notification.setTitre(title);
        notification.setMessage(message);
        notification.setType(TypeMessage.valueOf(type));
        notification.setDate(LocalDateTime.now());
        notification.setStatut(false);

        return notificationRepository.save(notification);
    }

    public List<Notification> getUsersNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedDesc(userId);
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalse(userId);
    }

    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setStatut(true);
            notificationRepository.save(notification);
        });
    }
}