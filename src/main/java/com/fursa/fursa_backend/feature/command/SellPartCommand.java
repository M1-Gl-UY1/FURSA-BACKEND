// SellPartCommand.java - Corrigé
package com.fursa.fursa_backend.feature.command;

import com.fursa.fursa_backend.model.*;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.service.BlockchainService;
import com.fursa.fursa_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class SellPartCommand implements Command {

    private final BlockchainService blockchainService;
    private final PossessionRepository possessionRepository;
    private final AnnonceRepository annonceRepository;
    private final NotificationService notificationService;

    private final Long proprieteId;
    private final Long vendeurId;
    private final Integer nombreParts;
    private final Double prixUnitairePart;

    private String transactionHash;
    private Annonce annonceCreee;

    @Override
    @Transactional
    public void execute() {
        Possession possession = possessionRepository.findByInvestisseurIdAndProprieteId(vendeurId, proprieteId)
                .orElseThrow(() -> new RuntimeException("Aucune possession trouvée pour cette propriété"));

        if (possession.getNombreDeParts() < nombreParts) {
            throw new RuntimeException("Parts insuffisantes en possession");
        }

        annonceCreee = new Annonce();
        annonceCreee.setInvestisseur(possession.getInvestisseur());
        annonceCreee.setPropriete(possession.getPropriete());
        annonceCreee.setNombreDePartsAVendre(nombreParts);
        annonceCreee.setPrixUnitaireDemande(BigDecimal.valueOf(prixUnitairePart));
        annonceCreee.setPrixTotal(nombreParts * prixUnitairePart);
        annonceCreee.setStatut(StatutAnnonce.OUVERTE);
        annonceCreee.setDateCreation(LocalDateTime.now());
        annonceCreee.setDateExpiration(LocalDateTime.now().plusDays(30));

        annonceRepository.save(annonceCreee);

        transactionHash = blockchainService.lockSharesOnChain(String.valueOf(vendeurId), String.valueOf(proprieteId), nombreParts);

        // Correction: Utiliser getInvestisseur().getId() au lieu de investisseur directement
        notificationService.createNotification(
                possession.getInvestisseur().getId(),
                "Annonce créée",
                "Vos " + nombreParts + " parts sont maintenant en vente",
                "MARCHE"
        );
    }

    @Override
    @Transactional
    public void undo() {
        if (annonceCreee != null) {
            annonceCreee.setStatut(StatutAnnonce.ANNULEE);
            annonceRepository.save(annonceCreee);

            if (transactionHash != null) {
                blockchainService.unlockShares(transactionHash);
            }
        }
    }

    @Override
    public String getTransactionHash() {
        return transactionHash;
    }
}