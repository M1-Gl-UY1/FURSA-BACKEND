package com.fursa.fursa_backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reponse enrichie pour l'admin : inclut les infos investisseur + audit interne.
 */
public record KycAdminResponse(
        Long id,
        String statut,
        Long investisseurId,
        String investisseurEmail,
        String investisseurNom,
        String investisseurPrenom,
        String investisseurTelephone,
        Boolean investisseurIsVerified,
        String nationalite,
        LocalDate dateNaissance,
        String paysResidence,
        String adresse,
        String documentIdentiteUrl,
        String documentDomicileUrl,
        String selfieUrl,
        String sourceFonds,
        Boolean isPep,
        Boolean declarationSurHonneur,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        Long reviewedByAdminId,
        String motifRefus,
        Integer nombreReSubmissions
) {}
