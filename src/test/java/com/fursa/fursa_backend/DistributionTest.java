package com.fursa.fursa_backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fursa.fursa_backend.dividend_Calculation.services.DistributionServiceImplement;
import com.fursa.fursa_backend.dividend_Calculation.services.distributionStrategy.DistributionStrategy;
import com.fursa.fursa_backend.dividende.repository.DividendeRepository;
import com.fursa.fursa_backend.dividende.service.DividendeFactory;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.possession.repository.PossessionRepository;
import com.fursa.fursa_backend.revenus.repository.RevenusRepository;

@ExtendWith(MockitoExtension.class) // Remplace @SpringBootTest, beaucoup plus léger
class DistributionTest {

    @Mock private DividendeRepository dividendeRepo;
    @Mock private RevenusRepository revenusRepo;
    @Mock private PossessionRepository possessionRepository;
    @Mock private DividendeFactory dividendeFactory;
    @Mock private DistributionStrategy distributionStrategy;

    @InjectMocks
    private DistributionServiceImplement distributionService; // Mockito injecte les mocks ci-dessus dedans

    @Test
    public void testDistribution_Succes() {
        // --- 1. PRÉPARATION (Given) ---
        Long revenuId = 1L;
        
        // Simuler une propriété avec 1000 parts
        Propriete propriete = new Propriete();
        propriete.setId(10L);
        propriete.setNombreTotalPart(1000);

        // Simuler un revenu de 10 000€
        Revenus revenus = new Revenus();
        revenus.setMontantTotal(new BigDecimal("10000.00"));
        revenus.setPropriete(propriete);

        // Simuler 2 investisseurs (Jean 400 parts, Marie 600 parts)
        Investisseur jean = new Investisseur();
        jean.setEmail("jean@mail.com");
        Possession posJean = new Possession();
        posJean.setInvestisseur(jean);
        posJean.setNombreDeParts(400);

        Investisseur marie = new Investisseur();
        marie.setEmail("marie@mail.com");
        Possession posMarie = new Possession();
        posMarie.setInvestisseur(marie);
        posMarie.setNombreDeParts(600);

        // CONFIGURATION DES MOCKS
        when(revenusRepo.findById(revenuId)).thenReturn(Optional.of(revenus));
        when(possessionRepository.findByProprieteId(propriete.getId()))
                .thenReturn(Arrays.asList(posJean, posMarie));

        // Simuler les calculs de la stratégie
        when(distributionStrategy.calculerMontant(any(), eq(400), eq(1000)))
                .thenReturn(new BigDecimal("4000.00"));
        when(distributionStrategy.calculerMontant(any(), eq(600), eq(1000)))
                .thenReturn(new BigDecimal("6000.00"));

        // Simuler la factory qui crée des objets vides
        when(dividendeFactory.create(any(), any(), any())).thenReturn(new Dividende());

        // --- 2. EXÉCUTION (When) ---
        distributionService.distribuer(revenuId);

        // --- 3. VÉRIFICATION (Then) ---
        // On vérifie que la méthode save a été appelée exactement 2 fois (une fois par investisseur)
        verify(dividendeRepo, times(2)).save(any(Dividende.class));
    }

    @Test
    public void shouldThrowExceptionWhenRevenueNotFound() {
        when(revenusRepo.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> distributionService.distribuer(999L));

        assertEquals("Revenu non trouvé avec l'ID: 999", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenNoPossessions() {
        Revenus rev = new Revenus();
        Propriete prop = new Propriete();
        prop.setNombreTotalPart(100);
        rev.setPropriete(prop);

        when(revenusRepo.findById(2L)).thenReturn(Optional.of(rev));
        when(possessionRepository.findByProprieteId(any())).thenReturn(Arrays.asList());

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> distributionService.distribuer(2L));

        assertEquals("Aucune possession trouvée pour cette propriété", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenTotalPartsIsZero() {
        Revenus rev = new Revenus();
        Propriete prop = new Propriete();
        prop.setNombreTotalPart(0); // Cas critique
        rev.setPropriete(prop);

        when(revenusRepo.findById(4L)).thenReturn(Optional.of(rev));

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> distributionService.distribuer(4L));

        assertEquals("Le nombre total de parts est invalide (0)", exception.getMessage());
    }

    @Test
    public void shouldThrowExceptionWhenTotalPartsInvalid() {
        Revenus rev = new Revenus();
        Propriete prop = new Propriete();
        prop.setNombreTotalPart(-10); // Parts négatives
        rev.setPropriete(prop);

        when(revenusRepo.findById(3L)).thenReturn(Optional.of(rev));

        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> distributionService.distribuer(3L));

        assertEquals("Le nombre total de parts est invalide (0 ou négatif)", exception.getMessage());
    }
}