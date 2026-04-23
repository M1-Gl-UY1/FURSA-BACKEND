package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.DividendeResponse;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.repository.DividendeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DividendeQueryService {

    private final DividendeRepository dividendeRepository;

    public List<DividendeResponse> listerPour(Long investisseurId) {
        return dividendeRepository.findByInvestisseurId(investisseurId).stream().map(this::toResponse).toList();
    }

    public List<DividendeResponse> listerPourRevenu(Long revenuId) {
        return dividendeRepository.findByRevenusId(revenuId).stream().map(this::toResponse).toList();
    }

    public List<DividendeResponse> listerTous() {
        return dividendeRepository.findAll().stream().map(this::toResponse).toList();
    }

    private DividendeResponse toResponse(Dividende d) {
        var revenu = d.getRevenus();
        var propriete = revenu == null ? null : revenu.getPropriete();
        var investisseur = d.getInvestisseur();
        return new DividendeResponse(
                d.getId(),
                revenu == null ? null : revenu.getId(),
                propriete == null ? null : propriete.getId(),
                propriete == null ? null : propriete.getNom(),
                investisseur == null ? null : investisseur.getId(),
                d.getMontantCalcule(),
                d.getDateDistribution(),
                d.getStatut(),
                d.getHashTransaction()
        );
    }
}
