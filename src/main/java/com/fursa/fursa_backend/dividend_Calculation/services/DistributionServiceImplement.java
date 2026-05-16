package com.fursa.fursa_backend.dividend_Calculation.services;

import com.fursa.fursa_backend.blockchain.service.BlockchainService;
import com.fursa.fursa_backend.dividend_Calculation.services.distributionStrategy.DistributionStrategy;
import com.fursa.fursa_backend.dividende.repository.DividendeRepository;
import com.fursa.fursa_backend.dividende.service.DividendeFactory;
import com.fursa.fursa_backend.model.*;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.TypePaiement;
import com.fursa.fursa_backend.paiement.repository.PaiementRepository;
import com.fursa.fursa_backend.possession.repository.PossessionRepository;
import com.fursa.fursa_backend.revenus.repository.RevenusRepository;
import com.fursa.fursa_backend.Transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DistributionServiceImplement implements DistributionService {

    private final DividendeRepository dividendeRepo;
    private final RevenusRepository revenusRepo;
    private final PossessionRepository possessionRepository;
    private final DividendeFactory dividendeFactory;
    private final DistributionStrategy distributionStrategy;
    private final BlockchainService blockchainService;
    private final PaiementRepository paiementRepository;
    private final TransactionRepository transactionRepository;

    // ============================================================
    // ÉTAPE 1 — Calcul et sauvegarde des dividendes en BD
    // ============================================================
    @Override
    public void distribuer(Long revenuId) {
        Revenus revenus = revenusRepo.findById(revenuId)
            .orElseThrow(() -> new RuntimeException("Revenu non trouvé: " + revenuId));

        BigDecimal montantTotal = revenus.getMontantTotal();
        Propriete propriete = revenus.getPropriete();

        if (propriete == null)
            throw new RuntimeException("Aucune propriété associée");

        int totalParts = propriete.getNombreTotalPart();
        if (totalParts <= 0)
            throw new RuntimeException("Nombre total de parts invalide");

        List<Possession> listePossessions =
            possessionRepository.findByProprieteId(propriete.getId());

        if (listePossessions.isEmpty())
            throw new RuntimeException("Aucune possession trouvée");

        for (Possession possession : listePossessions) {
            BigDecimal montantADistribuer = distributionStrategy.calculerMontant(
                montantTotal,
                possession.getNombreDeParts(),
                totalParts
            );

            Dividende dividende = dividendeFactory.create(
                montantADistribuer,
                possession.getInvestisseur(),
                revenus
            );

            dividende.setStatut(StatutPaiement.VALIDE);
            dividendeRepo.save(dividende);
        }
    }

    // ============================================================
    // ÉTAPE 2 — Distribution via blockchain
    // ============================================================
    public void distribuerViaBlockchain(Long revenuId) {

        List<Dividende> dividendes = dividendeRepo
            .findByRevenusIdAndStatut(revenuId, StatutPaiement.VALIDE);

        if (dividendes.isEmpty())
            throw new RuntimeException(
                "Aucun dividende VALIDE trouvé pour le revenu " + revenuId +
                ". Lance d'abord GET /api/distribution/" + revenuId
            );

        // 1 ETH = 1 000 000 FCFA
        BigDecimal tauxFCFA = new BigDecimal("1000000");

        for (Dividende dividende : dividendes) {

            Investisseur investisseur = dividende.getInvestisseur();
            String walletAddress = investisseur.getWallet_address();

            if (walletAddress == null || walletAddress.isBlank()) {
                log.warn("Investisseur id={} sans wallet — ignoré",
                    investisseur.getId());
                continue;
            }

            // Conversion FCFA → ETH → Wei
            BigDecimal montantFCFA = dividende.getMontantCalcule();
            BigDecimal montantETH  = montantFCFA.divide(
                tauxFCFA, 18, RoundingMode.HALF_UP
            );
            BigInteger montantWei  = blockchainService.ethToWei(
                montantETH.doubleValue()
            );

            // Nombre de parts pour cette propriété
            Possession possession = possessionRepository
                .findByInvestisseurAndPropriete(
                    investisseur,
                    dividende.getRevenus().getPropriete()
                ).orElse(null);

            int nombreParts = (possession != null)
                ? possession.getNombreDeParts() : 0;

            log.info("→ Paiement {} FCFA ({} ETH / {} Wei) à {}",
                montantFCFA, montantETH, montantWei, walletAddress);

            try {
                // Appel smart contract
                String txHash = blockchainService.payInvestor(
                    walletAddress, montantWei
                );

                // Mise à jour dividende
                dividende.setHashTransaction(txHash);
                dividende.setStatut(StatutPaiement.PAYE);
                dividende.setDateDistribution(LocalDate.now());
                dividendeRepo.save(dividende);

                // Création Paiement
                Paiement paiement = new Paiement();
                paiement.setMontant(montantFCFA);
                paiement.setType(TypePaiement.CRYPTO);
                paiement.setStatut(StatutPaiement.PAYE);
                paiement.setDate(LocalDateTime.now());
                paiement.setNombre_parts(nombreParts);
                paiement.setPropriete(dividende.getRevenus().getPropriete());
                paiement.setInvestisseur(investisseur);
                Paiement savedPaiement = paiementRepository.save(paiement);

                // Création Transaction
                Transaction transaction = new Transaction();
                transaction.setHashTransaction(txHash);
                transaction.setTypeOperation("DISTRIBUTION_DIVIDENDE");
                transaction.setMontant(montantFCFA);
                transaction.setDateTransaction(LocalDateTime.now());
                transaction.setNombreParts(nombreParts);
                transaction.setPaiement(savedPaiement);
                transactionRepository.save(transaction);

                log.info("Payé {} FCFA à {} | Tx: {} | Parts: {}",
                    montantFCFA, walletAddress, txHash, nombreParts);

            } catch (Exception e) {
                log.error("Échec pour {} : {}", walletAddress, e.getMessage());
                dividende.setStatut(StatutPaiement.ECHOUE);
                dividendeRepo.save(dividende);
            }
        }
    }
}