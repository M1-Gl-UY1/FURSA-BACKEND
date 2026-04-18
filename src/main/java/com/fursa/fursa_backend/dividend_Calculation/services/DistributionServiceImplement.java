package com.fursa.fursa_backend.dividend_Calculation.services;

import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.fursa.fursa_backend.possession.repository.PossessionRepository;
import com.fursa.fursa_backend.dividende.repository.DividendeRepository;

import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;


@Service
@RequiredArgsConstructor // Remplace @AllArgsConstructor (plus propre ici)
@Transactional // Assure que tout est annulé si une erreur survient
public class DistributionServiceImplement implements DistributionService {

    private final DividendeRepository dividendeRepo;
    private final com.fursa.fursa_backend.revenus.repository.RevenusRepository revenusRepo;
    private final PossessionRepository possessionRepository;

    @Override
    public void distribuer(Long revenuId) {

        // =========================
        // 1. Vérifier et récupérer le revenu
        // =========================
        Revenus revenus = revenusRepo.findById(revenuId)
                .orElseThrow(() -> new RuntimeException(
                        "Revenu non trouvé avec l'ID: " + revenuId));

        // Montant total à distribuer
        BigDecimal montantTotal = revenus.getMontantTotal();

        // =========================
        // 2. Vérifier les propriétés associées
        // =========================
        Propriete proprietes = revenus.getPropriete();

        if (proprietes == null) {
            throw new RuntimeException("Aucune propriété associée à ce revenu");
        }

        //  plusieurs propriétés doivent être gérées
        Propriete propriete = proprietes;

        // 3. Récupérer les possessions (investisseurs + parts)
        List<Possession> listePossessions =
                possessionRepository.findByProprieteId(propriete.getId());

        if (listePossessions.isEmpty()) {
            throw new RuntimeException("Aucune possession trouvée pour cette propriété");
        }

        // Nombre total de parts de la propriété
        int totalParts = propriete.getNombreTotalPart();

        if (totalParts == 0) {
            throw new RuntimeException("Le nombre total de parts est invalide (0)");
        }

        // 4. Distribution des revenus

        for (Possession possession : listePossessions) {

            // Nombre de parts détenues par cet investisseur
            int partsInvestisseur = possession.getNombreDeParts();

            // -------------------------
            // Calcul du montant
            // Formule :
            // (montantTotal * partsInvestisseur) / totalParts

            BigDecimal montantADistribuer = montantTotal
                    .multiply(BigDecimal.valueOf(partsInvestisseur))
                    .divide(BigDecimal.valueOf(totalParts), 2, RoundingMode.HALF_UP);

            // 5. Création du dividende
            Dividende dividende = new Dividende();
            dividende.setMontantCalcule(montantADistribuer);
            dividende.setRevenus(revenus);
            dividende.setInvestisseur(possession.getInvestisseur());
            dividende.setStatut(com.fursa.fursa_backend.model.enumeration.StatutPaiement.VALIDE); 

            // 6. Sauvegarde en base
            dividendeRepo.save(dividende);
        }
    }
}