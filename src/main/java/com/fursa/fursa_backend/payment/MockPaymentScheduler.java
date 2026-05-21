package com.fursa.fursa_backend.payment;

import com.fursa.fursa_backend.model.PaymentSession;
import com.fursa.fursa_backend.model.enumeration.StatutPaymentSession;
import com.fursa.fursa_backend.payment.provider.MockPaymentProvider;
import com.fursa.fursa_backend.repository.PaymentSessionRepository;
import com.fursa.fursa_backend.service.MarchePrimaireService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Scheduler de dev qui auto-confirme les PaymentSession PENDING du provider MOCK.
 *
 * En attendant Session 2 (PaymentWebhookController + MarchePrimaireService.confirmerAchat),
 * ce composant ne fait QUE logguer les sessions qu'il aurait confirmees.
 *
 * Une fois Session 2 livree, on injectera le webhook handler et on appellera
 * processWebhook(payload simule) ici.
 *
 * Profil dev uniquement : aucun risque qu'il tourne en prod.
 */
@Component
@Profile("!prod")
public class MockPaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentScheduler.class);
    private static final Duration MIN_AGE_BEFORE_CONFIRM = Duration.ofSeconds(5);

    private final PaymentSessionRepository repository;
    private final MarchePrimaireService marchePrimaireService;

    public MockPaymentScheduler(PaymentSessionRepository repository,
                                MarchePrimaireService marchePrimaireService) {
        this.repository = repository;
        this.marchePrimaireService = marchePrimaireService;
    }

    @Scheduled(fixedDelay = 5000)
    public void autoConfirmMockSessions() {
        List<PaymentSession> pendingMockSessions = repository
                .findByStatutAndProviderName(StatutPaymentSession.PENDING, MockPaymentProvider.NAME);

        LocalDateTime threshold = LocalDateTime.now().minus(MIN_AGE_BEFORE_CONFIRM);

        for (PaymentSession session : pendingMockSessions) {
            if (session.getCreatedAt() == null || session.getCreatedAt().isAfter(threshold)) {
                continue;
            }
            // Simule un webhook PSP CONFIRMED en appelant directement le service.
            // Le montant recu = montant attendu (pas de frais simules en mode mock).
            WebhookEvent fakeEvent = new WebhookEvent(
                    WebhookEventType.PAYMENT_CONFIRMED,
                    session.getExternalId(),
                    new BigDecimal(session.getMontantUsdc().toPlainString()),
                    "USDC",
                    "mock_psp_tx_" + UUID.randomUUID(),
                    null
            );
            try {
                marchePrimaireService.confirmerAchat(session.getExternalId(), fakeEvent);
                log.info("[MOCK] Auto-confirmation OK pour PaymentSession id={} externalId={}",
                        session.getId(), session.getExternalId());
            } catch (Exception e) {
                log.error("[MOCK] Echec auto-confirmation pour session {} : {}",
                        session.getExternalId(), e.getMessage());
            }
        }
    }
}
