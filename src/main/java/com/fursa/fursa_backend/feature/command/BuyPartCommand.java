// BuyPartCommand.java - Corrigé
package com.fursa.fursa_backend.feature.command;

import com.fursa.fursa_backend.model.*;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.service.BlockchainService;
import com.fursa.fursa_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class BuyPartCommand implements Command {

    private final BlockchainService blockchainService;  // Changé: blockChainService -> blockchainService
    private final PossessionRepository possessionRepository;
    private final AnnonceRepository annonceRepository;
    private final NotificationService notificationService;

    private final Long proprieteId;
    private final Long acheteurId;
    private final Long annonceId;
    private final Integer nombreParts;
    private final Double montant;

    private String transactionHash;
    private Possession possessionVendeur;
    private Possession possessionAcheteur;
    private Annonce annonce;

    @Override
    @Transactional
    public void execute() {
        annonce = annonceRepository.findById(annonceId)
                .orElseThrow(() -> new RuntimeException("Annonce non trouvée"));

        if (annonce.getNombreDePartsAVendre() < nombreParts) {
            throw new RuntimeException("Parts insuffisantes dans l'annonce");
        }

        transactionHash = blockchainService.sendTransaction(String.valueOf(acheteurId), montant);
        boolean confirmed = blockchainService.waitForConfirmation(transactionHash);

        if (!confirmed) {
            throw new RuntimeException("Échec de confirmation de la transaction blockchain");
        }

        possessionVendeur = possessionRepository.findByInvestisseurIdAndProprieteId(
                        annonce.getInvestisseur().getId(), proprieteId)
                .orElseThrow(() -> new RuntimeException("Possession vendeur non trouvée"));

        int nouveauNombrePartsVendeur = possessionVendeur.getNombreDeParts() - nombreParts;
        possessionVendeur.setNombreDeParts(nouveauNombrePartsVendeur);
        possessionRepository.save(possessionVendeur);

        possessionAcheteur = possessionRepository.findByInvestisseurIdAndProprieteId(acheteurId, proprieteId)
                .orElse(null);

        if (possessionAcheteur == null) {
            possessionAcheteur = new Possession();
            Investisseur acheteur = new Investisseur();
            acheteur.setId(acheteurId);
            possessionAcheteur.setInvestisseur(acheteur);
            possessionAcheteur.setPropriete(annonce.getPropriete());
            possessionAcheteur.setNombreDeParts(0);
            possessionAcheteur.setDateAchat(LocalDateTime.now());
            possessionAcheteur.setPrixAchat(montant);
        }

        int nouveauNombrePartsAcheteur = possessionAcheteur.getNombreDeParts() + nombreParts;
        possessionAcheteur.setNombreDeParts(nouveauNombrePartsAcheteur);
        possessionRepository.save(possessionAcheteur);

        int nouveauNombrePartsAnnonce = annonce.getNombreDePartsAVendre() - nombreParts;
        annonce.setNombreDePartsAVendre(nouveauNombrePartsAnnonce);

        if (annonce.getNombreDePartsAVendre() == 0) {
            annonce.setStatut(StatutAnnonce.COMPLETEE);
        }
        annonceRepository.save(annonce);

        blockchainService.assignSharesOnChain(String.valueOf(acheteurId), String.valueOf(proprieteId), nombreParts);

        // Correction: Utiliser getId() au lieu de l'objet Investisseur directement
        notificationService.createNotification(
                annonce.getInvestisseur().getId(),
                "Parts Vendues",
                "Vous avez vendu " + nombreParts + " parts",
                "TRANSACTION"
        );

        notificationService.createNotification(
                acheteurId,
                "Achat Confirmé",
                "Vous avez acheté " + nombreParts + " parts",
                "TRANSACTION"
        );
    }

    @Override
    @Transactional
    public void undo() {
        if (transactionHash != null) {
            blockchainService.reverseTransaction(transactionHash);

            if (possessionVendeur != null) {
                int nouveauNombrePartsVendeur = possessionVendeur.getNombreDeParts() + nombreParts;
                possessionVendeur.setNombreDeParts(nouveauNombrePartsVendeur);
                possessionRepository.save(possessionVendeur);
            }

            if (possessionAcheteur != null) {
                int nouveauNombrePartsAcheteur = possessionAcheteur.getNombreDeParts() - nombreParts;
                possessionAcheteur.setNombreDeParts(nouveauNombrePartsAcheteur);
                possessionRepository.save(possessionAcheteur);
            }

            if (annonce != null) {
                int nouveauNombrePartsAnnonce = annonce.getNombreDePartsAVendre() + nombreParts;
                annonce.setNombreDePartsAVendre(nouveauNombrePartsAnnonce);

                if (annonce.getStatut() == StatutAnnonce.COMPLETEE) {
                    annonce.setStatut(StatutAnnonce.OUVERTE);
                }
                annonceRepository.save(annonce);
            }
        }
    }

    @Override
    public String getTransactionHash() {
        return transactionHash;
    }
}