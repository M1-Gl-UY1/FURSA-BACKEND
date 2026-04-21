// BuyOperation.java - Corrigé
package com.fursa.fursa_backend.feature.templateMethod;

import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.service.BlockchainService;
import com.fursa.fursa_backend.service.NotificationService;
import lombok.Setter;
import java.time.LocalDateTime;

@Setter
public class BuyOperation extends BlockchainOperationTemplate {

    private final PossessionRepository possessionRepository;
    private Long propertyId;
    private Long buyerId;
    private Integer partsCount;
    private Double amount;
    private Long sellerId;

    public BuyOperation(BlockchainService blockchainService,
                        NotificationService notificationService,
                        PossessionRepository possessionRepository) {
        super(blockchainService, notificationService);
        this.possessionRepository = possessionRepository;
    }

    @Override
    protected void validate() {
        if (propertyId == null || buyerId == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        if (partsCount <= 0 || amount <= 0) {
            throw new IllegalArgumentException("Invalid parts or amount");
        }

        log.info("Buy validation: {} parts for {}", partsCount, amount);
    }

    @Override
    protected String executeOnBlockchain() {
        return blockchainService.sendTransaction(String.valueOf(buyerId), amount);
    }

    @Override
    protected void updateDatabase(String txHash) {
        Possession buyerPossession = possessionRepository.findByInvestisseurIdAndProprieteId(buyerId, propertyId)
                .orElse(new Possession());

        if (buyerPossession.getId() == null) {
            Investisseur buyer = new Investisseur();
            buyer.setId(buyerId);
            buyerPossession.setInvestisseur(buyer);
            Propriete property = new Propriete();
            property.setId(propertyId);
            buyerPossession.setPropriete(property);
            buyerPossession.setNombreDeParts(0);
            buyerPossession.setDateAchat(LocalDateTime.now());
            buyerPossession.setPrixAchat(amount);
        }

        buyerPossession.setNombreDeParts(buyerPossession.getNombreDeParts() + partsCount);
        possessionRepository.save(buyerPossession);

        if (sellerId != null) {
            possessionRepository.findByInvestisseurIdAndProprieteId(sellerId, propertyId).ifPresent(sellerPossession -> {
                sellerPossession.setNombreDeParts(sellerPossession.getNombreDeParts() - partsCount);
                possessionRepository.save(sellerPossession);
            });
        }

        log.info("Database updated for purchase: tx={}", txHash);
    }

    @Override
    protected void notifySuccess() {
        // Correction: Utiliser buyerId (Long) au lieu d'Investisseur
        notificationService.createNotification(
                buyerId,
                "Purchase Successful",
                "Blockchain transaction confirmed: " + partsCount + " parts purchased",
                "TRANSACTION"
        );
    }
}