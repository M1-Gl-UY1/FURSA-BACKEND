package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.TypeMessage;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String titre,
        String message,
        TypeMessage type,
        LocalDateTime date,
        Boolean lu,
        /** Lien cliquable relatif (ex "/opportunites/12"). Null = pas de navigation. */
        String lien
) {}
