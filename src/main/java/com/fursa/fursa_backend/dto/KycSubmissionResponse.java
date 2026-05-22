package com.fursa.fursa_backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reponse renvoyee a l'investisseur (PAS d'infos admin internes type reviewedByAdminId).
 */
public record KycSubmissionResponse(
        Long id,
        String statut,
        String nationalite,
        LocalDate dateNaissance,
        String paysResidence,
        String adresse,
        String documentIdentiteUrl,
        String documentDomicileUrl,
        String selfieUrl,
        String sourceFonds,
        Boolean isPep,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String motifRefus,
        Integer nombreReSubmissions
) {}
