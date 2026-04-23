package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedInvestisseurService {

    public Investisseur current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User user)) {
            throw new AccessDeniedException("Aucun utilisateur authentifie");
        }
        if (!(user instanceof Investisseur inv)) {
            throw new AccessDeniedException("L'utilisateur courant n'est pas un investisseur");
        }
        return inv;
    }

    public Long currentId() {
        return current().getId();
    }
}
