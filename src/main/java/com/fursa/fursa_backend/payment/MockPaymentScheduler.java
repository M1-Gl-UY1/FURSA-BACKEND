package com.fursa.fursa_backend.payment;

import com.fursa.fursa_backend.model.PaymentSession;
import com.fursa.fursa_backend.model.enumeration.StatutPaymentSession;
import com.fursa.fursa_backend.payment.provider.MockPaymentProvider;
import com.fursa.fursa_backend.repository.PaymentSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

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

    public MockPaymentScheduler(PaymentSessionRepository repository) {
        this.repository = repository;
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
            // TODO Session 2 : construire un payload webhook simule et le passer au
            // PaymentWebhookController (ou directement a MarchePrimaireService.confirmerAchat).
            log.info("[MOCK] Auto-confirmation a venir pour PaymentSession id={} externalId={} (Session 2)",
                    session.getId(), session.getExternalId());
        }
    }
}
