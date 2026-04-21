package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.CreateAnnonceRequest;
import com.fursa.fursa_backend.dto.PurchaseResult;
import com.fursa.fursa_backend.model.*;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecondaryMarketService {

    private final AnnonceRepository annonceRepository;
    private final PossessionRepository possessionRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final BlockchainService blockchainService;

    @Transactional
    public Annonce createAnnonce(CreateAnnonceRequest request, Long vendeurId) {

        Possession possession = possessionRepository
                .findByInvestisseurIdAndProprieteId(vendeurId, request.getProprieteId())
                .orElseThrow(() -> new RuntimeException("Vous ne possédez pas cette propriété"));

        if (possession.getNombreDeParts() < request.getNombreParts()) {
            throw new RuntimeException(
                    "Nombre de parts insuffisant. Vous possédez " + possession.getNombreDeParts()
            );
        }

        Annonce annonce = new Annonce();
        annonce.setPrixUnitaireDemande(request.getPrixUnitairePart());
        annonce.setNombreDePartsAVendre(request.getNombreParts());
        annonce.setStatut(StatutAnnonce.OUVERTE);
        annonce.setInvestisseur(possession.getInvestisseur());
        annonce.setPropriete(possession.getPropriete());
        annonce.setPrixTotal(
                request.getPrixUnitairePart().doubleValue() * request.getNombreParts()
        );
        annonce.setDateCreation(LocalDateTime.now());
        annonce.setDateExpiration(LocalDateTime.now().plusDays(30));

        blockchainService.lockSharesOnChain(
                possession.getInvestisseur().getUser().getEmail(),
                request.getProprieteId().toString(),
                request.getNombreParts()
        );

        Annonce saved = annonceRepository.save(annonce);

        notificationService.createNotification(
                possession.getInvestisseur().getUser(),
                "Annonce créée",
                request.getNombreParts() + " parts mises en vente",
                "SUCCESS"
        );

        log.info("Annonce créée: {} parts par vendeur {}", request.getNombreParts(), vendeurId);

        return saved;
    }

    @Transactional
    public PurchaseResult acheterParts(AchatRequest request, Long acheteurId) {
        Annonce annonce = annonceRepository.findById(request.getAnnonceId())
                .orElseThrow(() -> new RuntimeException("Annonce non trouvée"));

        if (!annonce.isActive()) {
            throw new RuntimeException("Cette annonce n'est plus disponible");
        }

        if (annonce.getInvestisseur().getId().equals(acheteurId)) {
            throw new RuntimeException("Vous ne pouvez pas acheter vos propres parts");
        }

        if (request.getNombreParts() > annonce.getNombreDePartsAVendre()) {
            throw new RuntimeException("Nombre de parts demandé non disponible. Restant: " +
                    annonce.getNombreDePartsAVendre());
        }

        Double montantTotal = annonce.getPrixUnitaireDemande()
                .multiply(BigDecimal.valueOf(request.getNombreParts())).doubleValue();

        Possession possessionVendeur = possessionRepository
                .findByInvestisseurIdAndProprieteId(annonce.getInvestisseur().getId(), annonce.getPropriete().getId())
                .orElseThrow(() -> new RuntimeException("Erreur: vendeur n'a plus de parts"));

        Possession possessionAcheteur = possessionRepository
                .findByInvestisseurIdAndProprieteId(acheteurId, annonce.getPropriete().getId())
                .orElse(null);

        // Correction: utiliser email directement depuis Investisseur
        String txHash = blockchainService.sendTransaction(
                annonce.getInvestisseur().getEmail(),
                montantTotal
        );

        possessionVendeur.setNombreDeParts(possessionVendeur.getNombreDeParts() - request.getNombreParts());
        possessionRepository.save(possessionVendeur);

        if (possessionAcheteur == null) {
            possessionAcheteur = new Possession();
            possessionAcheteur.setInvestisseur(annonce.getInvestisseur());
            possessionAcheteur.setPropriete(annonce.getPropriete());
            possessionAcheteur.setNombreDeParts(0);
            possessionAcheteur.setPrixAchat(0.0);
            possessionAcheteur.setDateAchat(LocalDateTime.now());
        }
        possessionAcheteur.setNombreDeParts(possessionAcheteur.getNombreDeParts() + request.getNombreParts());
        possessionAcheteur.setPrixAchat(montantTotal);
        possessionRepository.save(possessionAcheteur);

        annonce.setNombreDePartsAVendre(annonce.getNombreDePartsAVendre() - request.getNombreParts());
        if (annonce.getNombreDePartsAVendre() == 0) {
            annonce.setStatut(StatutAnnonce.COMPLETEE);
        }
        annonceRepository.save(annonce);

        Transaction transaction = new Transaction();
        transaction.setHashTransaction(txHash);
        transaction.setMontant(montantTotal);
        transaction.setNombreParts(request.getNombreParts());
        transaction.setTypeOperation("ACHAT_SECONDAIRE");
        transaction.setDateTransaction(LocalDateTime.now());
        transaction.setVendeur(annonce.getInvestisseur());
        transaction.setPropriete(annonce.getPropriete());
        transactionRepository.save(transaction);

        // Notifications avec userId
        notificationService.createNotification(
                annonce.getInvestisseur().getId(),
                "Vente réalisée",
                "Vos " + request.getNombreParts() + " parts ont été vendues pour " + montantTotal + "€",
                "SUCCESS"
        );

        notificationService.createNotification(
                acheteurId,
                "Achat réussi",
                "Vous avez acheté " + request.getNombreParts() + " parts pour " + montantTotal + "€",
                "SUCCESS"
        );

        log.info("Achat réalisé: {} parts", request.getNombreParts());

        return PurchaseResult.builder()
                .success(true)
                .transactionHash(txHash)
                .montantTotal(montantTotal)
                .nombreParts(request.getNombreParts())
                .message("Achat effectué avec succès")
                .build();
    }

    @Transactional
    public void cancelAnnonce(Long annonceId, Long vendeurId) {

        Annonce annonce = annonceRepository.findById(annonceId)
                .orElseThrow(() -> new RuntimeException("Annonce non trouvée"));

        if (!annonce.getInvestisseur().getId().equals(vendeurId)) {
            throw new RuntimeException("Non autorisé");
        }

        if (annonce.getStatut() != StatutAnnonce.OUVERTE) {
            throw new RuntimeException("Annonce non annulable");
        }

        annonce.setStatut(StatutAnnonce.ANNULEE);
        annonceRepository.save(annonce);

        notificationService.createNotification(
                annonce.getInvestisseur().getUser(),
                "Annonce annulée",
                "Votre annonce a été annulée",
                "SUCCESS"
        );

        log.info("Annonce annulée: {} par vendeur {}", annonceId, vendeurId);
    }
}