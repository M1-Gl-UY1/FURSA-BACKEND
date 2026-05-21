package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.PaymentSession;
import com.fursa.fursa_backend.model.enumeration.StatutPaymentSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentSessionRepository extends JpaRepository<PaymentSession, Long> {

    Optional<PaymentSession> findByExternalId(String externalId);

    /** Lock pessimistic pour serializer les webhooks concurrents qui confirmeraient la meme session. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentSession p where p.externalId = :externalId")
    Optional<PaymentSession> findByExternalIdForUpdate(@Param("externalId") String externalId);

    Optional<PaymentSession> findByIdempotencyKeyAndInvestisseur_Id(String idempotencyKey, Long investisseurId);

    List<PaymentSession> findByStatutAndProviderName(StatutPaymentSession statut, String providerName);

    List<PaymentSession> findByStatutAndExpiresAtBefore(StatutPaymentSession statut, LocalDateTime cutoff);
}
