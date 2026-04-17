package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.Investisseur;
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

    public  RegisterResponse(Investisseur user){
        id = user.getId();
        nom = user.getNom();
        prenom = user.getPrenom();
        telephone = user.getTelephone();
        email = user.getEmail();
        role = user.getRole();
    }
}
