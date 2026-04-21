// SellOperation.java - Corrigé
package com.fursa.fursa_backend.feature.templateMethod;

import com.fursa.fursa_backend.model.Annonce;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.service.BlockchainService;
import com.fursa.fursa_backend.service.NotificationService;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
public class SellOperation extends BlockchainOperationTemplate {

    private final AnnonceRepository annonceRepository;
    private Long propertyId;
    private Long sellerId;
    private Integer partsCount;
    private Double pricePerPart;

    public SellOperation(BlockchainService blockchainService,
                         NotificationService notificationService,
                         AnnonceRepository annonceRepository) {
        super(blockchainService, notificationService);
        this.annonceRepository = annonceRepository;
    }

    @Override
    protected void validate() {
        if (propertyId == null || sellerId == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        if (partsCount <= 0 || pricePerPart <= 0) {
            throw new IllegalArgumentException("Invalid parts or price");
        }
        log.info("Sell validation: {} parts at {} each", partsCount, pricePerPart);
    }

    @Override
    protected String executeOnBlockchain() {
        return blockchainService.lockSharesOnChain(String.valueOf(sellerId), String.valueOf(propertyId), partsCount);
    }

    @Override
    protected void updateDatabase(String txHash) {
        Annonce annonce = new Annonce();
        Investisseur seller = new Investisseur();
        seller.setId(sellerId);
        annonce.setInvestisseur(seller);
        Propriete property = new Propriete();
        property.setId(propertyId);
        annonce.setPropriete(property);
        annonce.setNombreDePartsAVendre(partsCount);
        annonce.setPrixUnitaireDemande(BigDecimal.valueOf(pricePerPart));
        annonce.setPrixTotal(partsCount * pricePerPart);
        annonce.setStatut(StatutAnnonce.OUVERTE);
        annonce.setDateCreation(LocalDateTime.now());
        annonce.setDateExpiration(LocalDateTime.now().plusDays(30));

        annonceRepository.save(annonce);
        log.info("Annonce created in database");
    }

    @Override
    protected void notifySuccess() {
        // Correction: Utiliser sellerId (Long) au lieu d'Investisseur
        notificationService.createNotification(
                sellerId,
                "Listing Successful",
                "Your " + partsCount + " parts are now on the market",
                "MARKETPLACE"
        );
    }
}