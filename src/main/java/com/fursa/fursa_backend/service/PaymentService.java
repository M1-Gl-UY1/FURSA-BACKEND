package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Paiement;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.TypePaiement;
import com.fursa.fursa_backend.repository.PaiementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaiementRepository paiementRepository;

    /**
     * Crée et traite un paiement (simulation)
     */
    public Paiement createAndProcessPayment(String paymentType, Double amount) {
        log.info("Création et traitement d'un paiement de {} € via {}", amount, paymentType);

        Paiement paiement = new Paiement();
        paiement.setMontant(BigDecimal.valueOf(amount));
        paiement.setType(TypePaiement.valueOf(paymentType.toUpperCase()));
        paiement.setStatut(StatutPaiement.EN_ATTENTE);
        paiement.setDate(LocalDateTime.now());

        // Simuler traitement
        try {
            // Simulation d'appel à un service externe de paiement
            String transactionId = simulateExternalPayment(amount, paymentType);

            if (transactionId != null) {
                paiement.setStatut(StatutPaiement.VALIDE);
                log.info("Paiement réussi. Transaction ID: {}", transactionId);
            } else {
                paiement.setStatut(StatutPaiement.ANNULE);
                log.error("Échec du paiement");
            }
        } catch (Exception e) {
            paiement.setStatut(StatutPaiement.ANNULE);
            log.error("Erreur lors du traitement du paiement: {}", e.getMessage());
        }

        return paiementRepository.save(paiement);
    }

    /**
     * Crée un paiement pour l'achat de parts
     */
    public Paiement createPaymentForShares(Investisseur investisseur, Propriete propriete,
                                           Integer nombreParts, BigDecimal prixUnitaire) {
        log.info("Création paiement pour {} parts de la propriété {} par investisseur {}",
                nombreParts, propriete.getId(), investisseur.getId());

        BigDecimal montantTotal = prixUnitaire.multiply(BigDecimal.valueOf(nombreParts));

        Paiement paiement = new Paiement();
        paiement.setMontant(montantTotal);
        paiement.setType(TypePaiement.CARTE_BANCAIRE);
        paiement.setStatut(StatutPaiement.EN_ATTENTE);
        paiement.setDate(LocalDateTime.now());
        paiement.setNombre_parts(nombreParts);
        paiement.setPropriete(propriete);
        paiement.setInvestisseur(investisseur);

        // Simuler traitement automatique
        processPayment(paiement);

        return paiementRepository.save(paiement);
    }

    /**
     * Traite un paiement existant
     */
    public Paiement processPayment(Paiement paiement) {
        log.info("Traitement du paiement ID: {}", paiement.getId());

        // Simulation de délai de traitement
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulation de succès (90% de succès)
        boolean success = Math.random() > 0.1;

        if (success) {
            paiement.setStatut(StatutPaiement.VALIDE);
            log.info("Paiement ID {} effectué avec succès", paiement.getId());
        } else {
            paiement.setStatut(StatutPaiement.ANNULE);
            log.error("Paiement ID {} échoué", paiement.getId());
        }

        return paiementRepository.save(paiement);
    }

    /**
     * Vérifie le statut d'un paiement
     */
    public StatutPaiement checkPaymentStatus(Long paymentId) {
        return paiementRepository.findById(paymentId)
                .map(Paiement::getStatut)
                .orElse(StatutPaiement.ANNULE);
    }

    /**
     * Rembourse un paiement
     */
    public boolean refundPayment(Long paymentId) {
        log.info("Demande de remboursement pour le paiement ID: {}", paymentId);

        Paiement paiement = paiementRepository.findById(paymentId).orElse(null);
        if (paiement == null) {
            log.error("Paiement non trouvé");
            return false;
        }

        if (paiement.getStatut() != StatutPaiement.VALIDE) {
            log.error("Impossible de rembourser un paiement non effectué");
            return false;
        }

        // Simulation de remboursement
        paiement.setStatut(StatutPaiement.REMBOURSE);
        paiementRepository.save(paiement);

        log.info("Paiement ID {} remboursé avec succès", paymentId);
        return true;
    }

    /**
     * Simulation d'appel à un service de paiement externe
     */
    private String simulateExternalPayment(Double amount, String paymentType) {
        // Simuler appel API Stripe/PayPal/...
        log.debug("Appel externe à {} pour {} €", paymentType, amount);

        // Générer un ID de transaction unique
        String transactionId = "TXN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Simuler un délai réseau
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        return transactionId;
    }

    /**
     * Validation simple de carte bancaire (simulation)
     */
    public boolean validateCard(String cardNumber, String expiryDate, String cvv) {
        // Simulation: carte valide si numéro commence par 4 ou 5 et a 16 chiffres
        boolean isValid = cardNumber != null &&
                cardNumber.matches("^[45]\\d{15}$") &&
                expiryDate != null &&
                expiryDate.matches("^(0[1-9]|1[0-2])/\\d{2}$") &&
                cvv != null &&
                cvv.matches("^\\d{3}$");

        log.info("Validation carte: {}", isValid ? "valide" : "invalide");
        return isValid;
    }
}