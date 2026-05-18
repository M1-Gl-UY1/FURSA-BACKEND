package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.blockchain.service.BlockchainService;
import com.fursa.fursa_backend.dividend_Calculation.services.distributionStrategy.DistributionStrategy;
import com.fursa.fursa_backend.dividende.service.DividendeFactory;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Paiement;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.model.Transaction;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.StatutRevenu;
import com.fursa.fursa_backend.model.enumeration.StatutTransaction;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.model.enumeration.TypeOperation;
import com.fursa.fursa_backend.model.enumeration.TypePaiement;
import com.fursa.fursa_backend.repository.DividendeRepository;
import com.fursa.fursa_backend.repository.PaiementRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.RevenusRepository;
import com.fursa.fursa_backend.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DistributionServiceImpl implements DistributionService {

    // Taux de conversion FCFA -> ETH utilise pour les paiements on-chain.
    // TODO post-merge : aligner avec la decision archi (stablecoins USDC/USDT/EURC sur Polygon),
    // au lieu d'envoyer du native token a un taux arbitraire.
    private static final BigDecimal TAUX_FCFA_PAR_ETH = new BigDecimal("1000000");

    private final RevenusRepository revenusRepository;
    private final PossessionRepository possessionRepository;
    private final DividendeRepository dividendeRepository;
    private final PaiementRepository paiementRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final DistributionStrategy distributionStrategy;
    private final DividendeFactory dividendeFactory;
    private final BlockchainService blockchainService;

    /**
     * Phase 1 : calcule et persiste les dividendes (statut VALIDE) pour un revenu.
     * Le revenu doit etre VALIDE. Marque le revenu DISTRIBUE en fin de traitement.
     */
    @Override
    public List<Dividende> distribuer(Long revenuId) {
        Revenus revenus = revenusRepository.findById(revenuId)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + revenuId));

        if (revenus.getStatut() != null && revenus.getStatut() != StatutRevenu.VALIDE) {
            throw new IllegalStateException(
                "Seuls les revenus VALIDE peuvent etre distribues (statut actuel : " + revenus.getStatut() + ")"
            );
        }

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
        NumberFormat eurFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE);

        List<Dividende> dividendes = new ArrayList<>();
        for (Possession possession : possessions) {
            int partsInvestisseur = possession.getNombreDeParts() == null ? 0 : possession.getNombreDeParts();
            if (partsInvestisseur <= 0) continue;

            BigDecimal montant = distributionStrategy.calculerMontant(
                montantTotal, partsInvestisseur, totalParts
            );

            Dividende dividende = dividendeFactory.create(montant, possession.getInvestisseur(), revenus);
            dividende.setDateDistribution(aujourdhui);
            dividende.setHashTransaction(UUID.randomUUID().toString());
            dividendes.add(dividendeRepository.save(dividende));

            Investisseur inv = possession.getInvestisseur();
            if (inv != null) {
                notificationService.envoyer(
                        inv,
                        "Dividende recu",
                        "Vous avez recu " + eurFormat.format(montant) + " de dividende pour \"" + propriete.getNom() + "\".",
                        TypeMessage.TRANSACTION
                );
            }
        }

        if (revenus.getStatut() != null) {
            revenus.setStatut(StatutRevenu.DISTRIBUE);
            revenusRepository.save(revenus);
        }

        return dividendes;
    }

    /**
     * Phase 2 : execute on-chain les dividendes VALIDE (calcules en phase 1) pour un revenu.
     * Pour chaque dividende : conversion FCFA -> ETH -> Wei, appel blockchainService.payInvestor,
     * mise a jour du statut (PAYE / ECHOUE), creation d'un Paiement + Transaction de tracage.
     */
    @Override
    public List<Dividende> distribuerViaBlockchain(Long revenuId) {
        List<Dividende> dividendes = dividendeRepository
                .findByRevenusIdAndStatut(revenuId, StatutPaiement.VALIDE);

        if (dividendes.isEmpty()) {
            throw new IllegalStateException(
                "Aucun dividende VALIDE trouve pour le revenu " + revenuId +
                ". Lancer d'abord la phase 1 (distribuer)."
            );
        }

        // Pre-alimentation du contrat avec le total a distribuer (sinon payInvestor revert
        // sur require(address(this).balance >= _amount) du RevenueDistribution.sol).
        BigInteger totalWei = BigInteger.ZERO;
        for (Dividende d : dividendes) {
            Investisseur inv = d.getInvestisseur();
            if (inv == null || inv.getWallet_address() == null || inv.getWallet_address().isBlank()) continue;
            BigDecimal montantETH = d.getMontantCalcule().divide(TAUX_FCFA_PAR_ETH, 18, RoundingMode.HALF_UP);
            totalWei = totalWei.add(blockchainService.ethToWei(montantETH.doubleValue()));
        }
        if (totalWei.compareTo(BigInteger.ZERO) > 0) {
            try {
                String fundTxHash = blockchainService.fundContract(totalWei);
                log.info("Contrat pre-alimente : {} wei pour revenu {} (tx={})", totalWei, revenuId, fundTxHash);
            } catch (Exception e) {
                log.error("Echec pre-alimentation du contrat pour revenu {} : {}", revenuId, e.getMessage());
                throw new IllegalStateException("Impossible d'alimenter le contrat : " + e.getMessage(), e);
            }
        }

        NumberFormat eurFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE);
        List<Dividende> traites = new ArrayList<>();

        for (Dividende dividende : dividendes) {
            Investisseur investisseur = dividende.getInvestisseur();
            if (investisseur == null) {
                log.warn("Dividende id={} sans investisseur, ignore", dividende.getId());
                continue;
            }

            String walletAddress = investisseur.getWallet_address();
            if (walletAddress == null || walletAddress.isBlank()) {
                log.warn("Investisseur id={} sans wallet, dividende {} ignore",
                        investisseur.getId(), dividende.getId());
                continue;
            }

            BigDecimal montantFCFA = dividende.getMontantCalcule();
            BigDecimal montantETH = montantFCFA.divide(TAUX_FCFA_PAR_ETH, 18, RoundingMode.HALF_UP);
            BigInteger montantWei = blockchainService.ethToWei(montantETH.doubleValue());

            Propriete propriete = dividende.getRevenus().getPropriete();
            Possession possession = possessionRepository
                    .findByInvestisseurIdAndProprieteId(investisseur.getId(), propriete.getId())
                    .orElse(null);
            int nombreParts = (possession != null && possession.getNombreDeParts() != null)
                    ? possession.getNombreDeParts() : 0;

            log.info("Distribution dividende id={} : {} FCFA -> {} ETH -> {} Wei vers {}",
                    dividende.getId(), montantFCFA, montantETH, montantWei, walletAddress);

            try {
                String txHash = blockchainService.payInvestor(walletAddress, montantWei);

                dividende.setHashTransaction(txHash);
                dividende.setStatut(StatutPaiement.PAYE);
                dividende.setDateDistribution(LocalDate.now());
                dividendeRepository.save(dividende);

                Paiement paiement = new Paiement();
                paiement.setMontant(montantFCFA);
                paiement.setType(TypePaiement.CRYPTO);
                paiement.setStatut(StatutPaiement.PAYE);
                paiement.setDate(LocalDateTime.now());
                paiement.setNombre_parts(nombreParts);
                paiement.setPropriete(propriete);
                paiement.setInvestisseur(investisseur);
                Paiement savedPaiement = paiementRepository.save(paiement);

                Transaction transaction = new Transaction();
                transaction.setHashTransaction(txHash);
                transaction.setTypeOperation(TypeOperation.DIVIDENDE);
                transaction.setMontant(montantFCFA);
                transaction.setDateTransaction(LocalDateTime.now());
                transaction.setNombreParts(nombreParts);
                transaction.setStatut(StatutTransaction.SUCCES);
                transaction.setPaiement(savedPaiement);
                transactionRepository.save(transaction);

                notificationService.envoyer(
                        investisseur,
                        "Dividende paye",
                        "Votre dividende de " + eurFormat.format(montantFCFA) +
                                " a ete paye on-chain. Tx: " + txHash,
                        TypeMessage.TRANSACTION
                );

                traites.add(dividende);

            } catch (Exception e) {
                log.error("Echec paiement on-chain dividende id={} vers {} : {}",
                        dividende.getId(), walletAddress, e.getMessage());
                dividende.setStatut(StatutPaiement.ECHOUE);
                dividendeRepository.save(dividende);
            }
        }

        return traites;
    }
}
