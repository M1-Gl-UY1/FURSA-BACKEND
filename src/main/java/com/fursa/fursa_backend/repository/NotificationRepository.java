package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
        SELECT n 
        FROM Notification n 
        WHERE n.investisseur.user.id = :userId 
        ORDER BY n.date DESC
    """)
    List<Notification> findByUserIdOrderByCreatedDesc(@Param("userId") Long userId);

    @Query("""
        SELECT n 
        FROM Notification n 
        WHERE n.investisseur.user.id = :userId 
          AND n.statut = false 
        ORDER BY n.date DESC
    """)
    List<Notification> findByUserIdAndIsReadFalse(@Param("userId") Long userId);
}