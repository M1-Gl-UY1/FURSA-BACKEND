package com.fursa.fursa_backend.dividend_Calculation.services;

import com.fursa.fursa_backend.dividend_Calculation.services.distributionStrategy.DistributionStrategy;
import com.fursa.fursa_backend.dividende.repository.DividendeRepository;
import com.fursa.fursa_backend.dividende.service.DividendeFactory;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.possession.repository.PossessionRepository;
import com.fursa.fursa_backend.revenus.repository.RevenusRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implémentation du service de distribution des dividendes.
 * Ce service utilise les patrons de conception Strategy (pour le calcul) 
 * et Factory (pour la création d'objets).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DistributionServiceImplement implements DistributionService {

    private final DividendeRepository dividendeRepo;
    private final RevenusRepository revenusRepo;
    private final PossessionRepository possessionRepository;
    private final DividendeFactory dividendeFactory;
    private final DistributionStrategy distributionStrategy;

    @Override
    public void distribuer(Long revenuId) {

        // ============================================================
        // 1. RÉCUPÉRATION ET VÉRIFICATION DU REVENU
        // ============================================================
        Revenus revenus = revenusRepo.findById(revenuId)
                .orElseThrow(() -> new RuntimeException("Revenu non trouvé avec l'ID: " + revenuId));

        BigDecimal montantTotal = revenus.getMontantTotal();

        // ============================================================
        // 2. VÉRIFICATION DE LA PROPRIÉTÉ ASSOCIÉE
        // ============================================================
        Propriete propriete = revenus.getPropriete();
        if (propriete == null) {
            throw new RuntimeException("Aucune propriété associée à ce revenu");
        }

        // ============================================================
        // 3. VÉRIFICATION DE LA VALIDITÉ DES PARTS (AVANT LES POSSESSIONS)
        // ============================================================
        int totalParts = propriete.getNombreTotalPart();

        // Cas spécifique pour le test : shouldThrowExceptionWhenTotalPartsIsZero
        if (totalParts == 0) {
            throw new RuntimeException("Le nombre total de parts est invalide (0)");
        }

        // Cas spécifique pour le test : shouldThrowExceptionWhenTotalPartsInvalid
        if (totalParts < 0) {
            throw new RuntimeException("Le nombre total de parts est invalide (0 ou négatif)");
        }

        // ============================================================
        // 4. RÉCUPÉRATION DES POSSESSIONS (Investisseurs + Parts)
        // ============================================================
        List<Possession> listePossessions = possessionRepository.findByProprieteId(propriete.getId());

        if (listePossessions.isEmpty()) {
            throw new RuntimeException("Aucune possession trouvée pour cette propriété");
        }

        // ============================================================
        // 5. DISTRIBUTION ET CALCUL (Itération sur chaque possesseur)
        // ============================================================
        for (Possession possession : listePossessions) {
            int partsInvestisseur = possession.getNombreDeParts();

            // Utilisation du Strategy Pattern pour le calcul du montant
            BigDecimal montantADistribuer = distributionStrategy.calculerMontant(
                    montantTotal,
                    partsInvestisseur,
                    totalParts
            );

            // Création du dividende via le Pattern Factory
            Dividende dividende = dividendeFactory.create(
                    montantADistribuer,
                    possession.getInvestisseur(),
                    revenus
            );

            // Persistance en base de données
            dividendeRepo.save(dividende);
        }
    }
}