package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.SourceFonds;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload de soumission KYC (envoye en multipart avec les fichiers separes).
 * Les fichiers (documentIdentite, documentDomicile, selfie) sont envoyes via @RequestParam MultipartFile.
 */
public record KycSubmitRequest(
        @NotBlank @Size(max = 100) String nationalite,
        @NotNull @Past LocalDate dateNaissance,
        @NotBlank @Size(max = 100) String paysResidence,
        @NotBlank @Size(max = 1000) String adresse,
        @NotNull SourceFonds sourceFonds,
        @NotNull Boolean isPep,
        @AssertTrue(message = "Vous devez accepter la declaration sur l'honneur")
        Boolean declarationSurHonneur
) {}
