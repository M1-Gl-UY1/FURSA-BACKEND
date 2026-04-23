package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.repository.DividendeRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.RevenusRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DistributionServiceImpl implements DistributionService {

    private final RevenusRepository revenusRepository;
    private final PossessionRepository possessionRepository;
    private final DividendeRepository dividendeRepository;

    @Override
    public List<Dividende> distribuer(Long revenuId) {
        Revenus revenus = revenusRepository.findById(revenuId)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + revenuId));

        Propriete propriete = revenus.getPropriete();
        if (propriete == null) {
            throw new IllegalStateException("Aucune propriete associee au revenu " + revenuId);
        }

        int totalParts = propriete.getNombreTotalPart() == null ? 0 : propriete.getNombreTotalPart();
        if (totalParts <= 0) {
            throw new IllegalStateException("Nombre total de parts invalide pour la propriete " + propriete.getId());
        }

        List<Possession> possessions = possessionRepository.findByProprieteId(propriete.getId());
        if (possessions.isEmpty()) {
            throw new IllegalStateException("Aucune possession pour la propriete " + propriete.getId());
        }

        BigDecimal montantTotal = revenus.getMontantTotal();
        LocalDate aujourdhui = LocalDate.now();

        List<Dividende> dividendes = new ArrayList<>();
        for (Possession possession : possessions) {
            int partsInvestisseur = possession.getNombreDeParts() == null ? 0 : possession.getNombreDeParts();
            if (partsInvestisseur <= 0) continue;

            BigDecimal montant = montantTotal
                    .multiply(BigDecimal.valueOf(partsInvestisseur))
                    .divide(BigDecimal.valueOf(totalParts), 2, RoundingMode.HALF_UP);

            Dividende dividende = new Dividende();
            dividende.setMontantCalcule(montant);
            dividende.setDateDistribution(aujourdhui);
            dividende.setStatut(StatutPaiement.VALIDE);
            dividende.setHashTransaction(UUID.randomUUID().toString());
            dividende.setRevenus(revenus);
            dividende.setInvestisseur(possession.getInvestisseur());
            dividendes.add(dividendeRepository.save(dividende));
        }

        return dividendes;
    }
}
