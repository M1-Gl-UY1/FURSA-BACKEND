// NotificationDTO.java - Version corrigée
package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.Notification;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import lombok.Data;

import java.time.format.DateTimeFormatter;

@Data
public class NotificationDTO {
    private Long id;
    private String titre;
    private String message;
    private String type;
    private String date;
    private Boolean statut;
    private Long investisseurId;
    private String investisseurNom;

    public static NotificationDTO from(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setTitre(notification.getTitre());
        dto.setMessage(notification.getMessage());

        // Correction: notification.getType() retourne TypeMessage, pas String
        if (notification.getType() != null) {
            dto.setType(notification.getType().name());
        }

        // Correction: format LocalDateTime to String
        if (notification.getDate() != null) {
            dto.setDate(notification.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        dto.setStatut(notification.getStatut());

        // Ajouter infos investisseur
        if (notification.getInvestisseur() != null) {
            dto.setInvestisseurId(notification.getInvestisseur().getId());
            dto.setInvestisseurNom(notification.getInvestisseur().getNom() + " " +
                    notification.getInvestisseur().getPrenom());
        }

        return dto;
    }
}