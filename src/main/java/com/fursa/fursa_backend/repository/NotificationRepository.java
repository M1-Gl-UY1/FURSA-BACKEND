package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByDestinataireIdOrderByDateDesc(Long destinataireId);
    List<Notification> findByDestinataireIdAndLuFalseOrderByDateDesc(Long destinataireId);
}
