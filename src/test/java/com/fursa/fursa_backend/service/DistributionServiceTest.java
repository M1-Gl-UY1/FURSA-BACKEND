package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.blockchain.service.BlockchainService;
import com.fursa.fursa_backend.dividende.service.DividendeFactory;
import com.fursa.fursa_backend.dividend_Calculation.services.distributionStrategy.DistributionStrategy;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.repository.DividendeRepository;
import com.fursa.fursa_backend.repository.PaiementRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.RevenusRepository;
import com.fursa.fursa_backend.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributionServiceTest {

    @Mock private RevenusRepository revenusRepository;
    @Mock private PossessionRepository possessionRepository;
    @Mock private DividendeRepository dividendeRepository;
    @Mock private PaiementRepository paiementRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private NotificationService notificationService;
    @Mock private DistributionStrategy distributionStrategy;
    @Mock private DividendeFactory dividendeFactory;
    @Mock private BlockchainService blockchainService;

    @InjectMocks private DistributionServiceImpl distributionService;

    private Propriete propriete;
    private Revenus revenus;
    private Investisseur jean;
    private Investisseur marie;

    @BeforeEach
    void setUp() {
        propriete = new Propriete();
        propriete.setId(1L);
        propriete.setNombreTotalPart(1000);

        revenus = new Revenus();
        revenus.setId(1L);
        revenus.setMontantTotal(new BigDecimal("10000.00"));
        revenus.setPropriete(propriete);
        // Phase 9 : la garde exige que l'argent ait ete confirme recu par FURSA
        revenus.setArgentRecuParFursa(true);

        jean = new Investisseur();
        jean.setId(10L);
        marie = new Investisseur();
        marie.setId(20L);
    }

    @Test
    void distribuer_calculeAuProrataDesParts() {
        Possession possJean = new Possession();
        possJean.setInvestisseur(jean);
        possJean.setNombreDeParts(400);
        Possession possMarie = new Possession();
        possMarie.setInvestisseur(marie);
        possMarie.setNombreDeParts(600);

        when(revenusRepository.findById(1L)).thenReturn(Optional.of(revenus));
        when(possessionRepository.findByProprieteId(1L)).thenReturn(List.of(possJean, possMarie));
        when(distributionStrategy.calculerMontant(any(BigDecimal.class), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> {
                    BigDecimal total = inv.getArgument(0);
                    int parts = inv.getArgument(1);
                    int totalParts = inv.getArgument(2);
                    return total.multiply(BigDecimal.valueOf(parts))
                            .divide(BigDecimal.valueOf(totalParts), 2, java.math.RoundingMode.HALF_UP);
                });
        when(dividendeFactory.create(any(BigDecimal.class), any(Investisseur.class), any(Revenus.class)))
                .thenAnswer(inv -> {
                    Dividende d = new Dividende();
                    d.setMontantCalcule(inv.getArgument(0));
                    d.setInvestisseur(inv.getArgument(1));
                    d.setRevenus(inv.getArgument(2));
                    return d;
                });
        when(dividendeRepository.save(any(Dividende.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Dividende> result = distributionService.distribuer(1L);

        assertEquals(2, result.size());
        assertEquals(0, new BigDecimal("4000.00").compareTo(result.get(0).getMontantCalcule()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(result.get(1).getMontantCalcule()));
        verify(dividendeRepository, times(2)).save(any(Dividende.class));
    }

    @Test
    void distribuer_revenuInexistant_leve404() {
        when(revenusRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> distributionService.distribuer(99L));
    }

    @Test
    void distribuer_sansPropriete_leveIllegalState() {
        Revenus orphelin = new Revenus();
        orphelin.setId(2L);
        orphelin.setMontantTotal(new BigDecimal("500.00"));
        orphelin.setPropriete(null);
        orphelin.setArgentRecuParFursa(true);
        when(revenusRepository.findById(2L)).thenReturn(Optional.of(orphelin));

        assertThrows(IllegalStateException.class, () -> distributionService.distribuer(2L));
    }

    @Test
    void distribuer_argentNonRecu_leveIllegalState() {
        Revenus pasEncoreRecu = new Revenus();
        pasEncoreRecu.setId(3L);
        pasEncoreRecu.setMontantTotal(new BigDecimal("1000.00"));
        pasEncoreRecu.setPropriete(propriete);
        pasEncoreRecu.setArgentRecuParFursa(false);
        when(revenusRepository.findById(3L)).thenReturn(Optional.of(pasEncoreRecu));

        assertThrows(IllegalStateException.class, () -> distributionService.distribuer(3L));
    }

    @Test
    void distribuer_sansPossession_leveIllegalState() {
        when(revenusRepository.findById(1L)).thenReturn(Optional.of(revenus));
        when(possessionRepository.findByProprieteId(1L)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> distributionService.distribuer(1L));
    }

    @Test
    void distribuer_totalPartsZero_leveIllegalState() {
        propriete.setNombreTotalPart(0);
        when(revenusRepository.findById(1L)).thenReturn(Optional.of(revenus));

        assertThrows(IllegalStateException.class, () -> distributionService.distribuer(1L));
    }
}
