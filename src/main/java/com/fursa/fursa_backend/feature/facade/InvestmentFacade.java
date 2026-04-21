// InvestmentFacade.java - Corrigé avec PaymentService
package com.fursa.fursa_backend.feature.facade;

import com.fursa.fursa_backend.model.Paiement;
import com.fursa.fursa_backend.model.Transaction;
import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.service.BlockchainService;
import com.fursa.fursa_backend.service.NotificationService;
import com.fursa.fursa_backend.service.PaymentService;
import com.fursa.fursa_backend.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvestmentFacade {

    private final BlockchainService blockchainService;
    private final PaymentService paymentService;
    private final TransactionService transactionService;
    private final PossessionRepository possessionRepository;
    private final NotificationService notificationService;

    public void invest(User user, String walletAddress, String propertyId,
                       Double amount, String paymentType) {

        log.info("Investissement: user={}, montant={}€, propriété={}", user.getEmail(), amount, propertyId);

        // 1. Create and process payment
        Paiement payment = paymentService.createAndProcessPayment(paymentType, amount);

        if (payment.getStatut() != StatutPaiement.VALIDE) {
            log.error("Échec du paiement");
            notificationService.createNotification(user, "Échec paiement",
                    "Le paiement de " + amount + "€ a échoué", "ERROR");
            return;
        }

        // 2. Send transaction to blockchain
        String txHash = blockchainService.sendTransaction(walletAddress, amount);

        // 3. Wait for confirmation
        boolean confirmed = blockchainService.waitForConfirmation(txHash);

        if (confirmed) {
            // 4. Create transaction in database
            Transaction transaction = transactionService.createTransaction(txHash, amount);

            // 5. Calculate and assign shares
            int partsCount = (int) (amount / getPropertyUnitPrice(propertyId));
            blockchainService.assignSharesOnChain(walletAddress, propertyId, partsCount);

            // 6. Notify investor
            notificationService.createNotification(user, "Investissement réussi",
                    "Vous avez investi " + amount + "€ dans la propriété", "SUCCESS");

            log.info("Investissement réussi: txHash={}, parts={}", txHash, partsCount);
        } else {
            log.error("Échec confirmation blockchain");
            notificationService.createNotification(user, "Échec transaction",
                    "La transaction blockchain a échoué", "ERROR");
        }
    }

    private Double getPropertyUnitPrice(String propertyId) {
        // Simulation: prix unitaire d'une part
        return 100.0;
    }
}