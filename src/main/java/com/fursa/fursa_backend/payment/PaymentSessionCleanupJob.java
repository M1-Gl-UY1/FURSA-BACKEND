package com.fursa.fursa_backend.payment;

import com.fursa.fursa_backend.model.PaymentSession;
import com.fursa.fursa_backend.model.enumeration.StatutPaymentSession;
import com.fursa.fursa_backend.repository.PaymentSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Marque EXPIRED les PaymentSession PENDING dont expires_at est depasse.
 * Tourne toutes les 60 secondes. Tous profils (un cron sain qui maintient la coherence DB).
 *
 * Distinct du MockPaymentScheduler : ici on ne CONFIRME pas, on EXPIRE.
 */
@Component
public class PaymentSessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentSessionCleanupJob.class);

    private final PaymentSessionRepository repository;

    public PaymentSessionCleanupJob(PaymentSessionRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 60000)  // toutes les 60s
    @Transactional
    public void expirePendingSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentSession> expired = repository.findByStatutAndExpiresAtBefore(StatutPaymentSession.PENDING, now);
        if (expired.isEmpty()) {
            return;
        }
        for (PaymentSession session : expired) {
            session.setStatut(StatutPaymentSession.EXPIRED);
            session.setErrorMessage("Session expiree automatiquement (expires_at depasse de "
                    + java.time.Duration.between(session.getExpiresAt(), now).toMinutes() + " min)");
        }
        repository.saveAll(expired);
        log.info("[CleanupJob] {} PaymentSession PENDING marquees EXPIRED", expired.size());
    }
}
