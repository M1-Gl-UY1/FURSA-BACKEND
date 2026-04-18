package com.fursa.fursa_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fursa.fursa_backend.dividend_Calculation.services.DistributionServiceImplement;
import com.fursa.fursa_backend.dividende.repository.DividendeRepository;
import com.fursa.fursa_backend.investisseur.InvestisseurRepository;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Investisseur;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@SpringBootTest
@Transactional
@RequiredArgsConstructor
public class DistributionTest {
    @Autowired
    private DistributionServiceImplement distributionService;

    @Autowired
    private DividendeRepository dividende;

    @Autowired
    private InvestisseurRepository investisseurRepo; // C'est cette variable qu'on utilise

    @Test
    public void testDistribution() {
        // 1 executer la distributionpour le revenu de 1
        distributionService.distribuer(1L);
                // 1. RÉCUPÉRER Jean et Marie depuis la BD (en supposant qu'ils s'appellent "Jean" et "Marie")
        // Note: Il faut que vous ayez une méthode findByNom dans votre InvestisseurRepository
        Investisseur jean = investisseurRepo.findByEmail("jean@mail.com")
                .orElseThrow(() -> new RuntimeException("Jean n'existe pas en BD"));
        Investisseur marie = investisseurRepo.findByEmail("marie@mail.com")
                .orElseThrow(() -> new RuntimeException("Marie n'existe pas en BD"));

        Long idJean = jean.getId();
        Long idMarie = marie.getId();

        // verification que les dividendes ont été créés pour les investisseurs
            List<Dividende> dividendesCrees = dividende.findAll(); // Récupère tous les dividendes créés
            assertEquals(2, dividendesCrees.size());

        // Vérifier que les dividendes ont été créés pour les investisseurs
        // Vérification pour Jean
        BigDecimal montantJean = dividendesCrees.stream()
            .filter(d -> d.getInvestisseur().getId().equals(idJean))
            .map(Dividende::getMontantCalcule)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Dividende pour Jean non trouvé"));

        assertEquals(0, new BigDecimal("4000.00").compareTo(montantJean));

        // Vérification pour Marie
        BigDecimal montantMarie = dividendesCrees.stream()
            .filter(d -> d.getInvestisseur().getId().equals(idMarie))
            .map(Dividende::getMontantCalcule)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Dividende pour Marie non trouvé"));

        assertEquals(0, new BigDecimal("6000.00").compareTo(montantMarie));

        // (Vous pouvez ajouter des assertions ici pour vérifier les résultats attendus)
    }
}
