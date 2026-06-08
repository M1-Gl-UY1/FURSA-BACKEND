package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.model.enumeration.Role;
import lombok.Data;

@Data
public class RegisterResponse {
    private Long id;
    private String nom;
    private String prenom;
    private String telephone;
    private String email;
    private Role role;
    private Boolean isVerified;
    private String walletAddress;
    private java.time.LocalDateTime deletedAt;

    public RegisterResponse(Investisseur user){
        id = user.getId();
        nom = user.getNom();
        prenom = user.getPrenom();
        telephone = user.getTelephone();
        email = user.getEmail();
        role = user.getRole();
        isVerified = user.getIsVerified();
        walletAddress = user.getWallet_address();
        deletedAt = user.getDeletedAt();
    }

    /**
     * V2 DD (08/06/2026) : constructor generique User pour le cas admin
     * (les admins n'ont pas nom/prenom/telephone/wallet_address dans la table
     * investisseur — seulement les champs communs id/email/role).
     */
    public RegisterResponse(User user){
        id = user.getId();
        email = user.getEmail();
        role = user.getRole();
        deletedAt = user.getDeletedAt();
        // Champs investisseur : laisse null pour les admins.
        if (user instanceof Investisseur inv) {
            nom = inv.getNom();
            prenom = inv.getPrenom();
            telephone = inv.getTelephone();
            isVerified = inv.getIsVerified();
            walletAddress = inv.getWallet_address();
        }
    }
}
