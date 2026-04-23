package com.fursa.fursa_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,

        @NotBlank
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir au moins 8 caracteres")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Le mot de passe doit contenir au moins une lettre et un chiffre"
        )
        String password,

        @NotBlank @Size(max = 100) String nom,
        @NotBlank @Size(max = 100) String prenom,

        @Pattern(regexp = "^\\+?[0-9\\s-]{6,20}$", message = "Numero de telephone invalide")
        String telephone,

        @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Wallet address invalide (format 0x + 40 hex)")
        String walletAddress
) {}
