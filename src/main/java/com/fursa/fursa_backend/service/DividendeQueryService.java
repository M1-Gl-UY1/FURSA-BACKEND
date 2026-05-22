package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.DividendeResponse;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.repository.DividendeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

    /**
     * Calcule la balance d'un investisseur :
     * - aRetirer : somme des dividendes statut VALIDE (calcules mais pas encore payes)
     * - dejaRecu : somme des dividendes statut PAYE (versement effectif confirme)
     * - total : aRetirer + dejaRecu
     * - nbARetirer / nbDejaRecu : compteurs
     */
    public Map<String, Object> balanceFor(Long investisseurId) {
        var divs = dividendeRepository.findByInvestisseurId(investisseurId);
        BigDecimal aRetirer = BigDecimal.ZERO;
        BigDecimal dejaRecu = BigDecimal.ZERO;
        int nbARetirer = 0;
        int nbDejaRecu = 0;
        for (Dividende d : divs) {
            BigDecimal m = d.getMontantCalcule() == null ? BigDecimal.ZERO : d.getMontantCalcule();
            if (d.getStatut() == StatutPaiement.PAYE) {
                dejaRecu = dejaRecu.add(m);
                nbDejaRecu++;
            } else if (d.getStatut() == StatutPaiement.VALIDE) {
                aRetirer = aRetirer.add(m);
                nbARetirer++;
            }
            // EN_ATTENTE / ECHOUE : ignores
        }
        return Map.of(
                "aRetirer", aRetirer,
                "dejaRecu", dejaRecu,
                "total", aRetirer.add(dejaRecu),
                "nbARetirer", nbARetirer,
                "nbDejaRecu", nbDejaRecu
        );
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
                d.getHashTransaction(),
                d.getDatePaiementEffectif(),
                d.getPreuvePaiement(),
                d.getMethodePaiement()
        );
    }
}
