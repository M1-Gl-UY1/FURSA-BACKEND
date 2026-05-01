package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.repository.ProprieteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bean utilisé dans @PreAuthorize pour vérifier qu'un user est le proposeur d'une propriété.
 * Usage : @PreAuthorize("hasRole('ADMIN') or @proprieteSecurity.isProposeur(#id, principal.id)")
 */
@Component("proprieteSecurity")
@RequiredArgsConstructor
public class ProprieteSecurity {

    private final ProprieteRepository proprieteRepository;

    public boolean isProposeur(Long proprieteId, Long userId) {
        if (proprieteId == null || userId == null) return false;
        return proprieteRepository.findById(proprieteId)
                .map(p -> userId.equals(p.getProposeurId()))
                .orElse(false);
    }
}
