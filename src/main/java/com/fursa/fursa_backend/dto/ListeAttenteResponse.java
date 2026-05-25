package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.StatutListeAttente;

import java.time.LocalDateTime;

public record ListeAttenteResponse(
        Long id,
        Long proprieteId,
        String proprieteNom,
        String proprieteLocalisation,
        Long investisseurId,
        Integer nombreParts,
        StatutListeAttente statut,
        /** Position dans la file FIFO (1 = premier). Null si pas EN_ATTENTE. */
        Integer position,
        LocalDateTime createdAt,
        LocalDateTime notifieLe,
        LocalDateTime serviLe
) {}
