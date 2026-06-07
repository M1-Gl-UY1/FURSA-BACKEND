package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.BlockchainSyncTask;
import com.fursa.fursa_backend.model.enumeration.StatutSyncBlockchain;
import com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain;
import com.fursa.fursa_backend.repository.BlockchainSyncTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2 U (07/06/2026) : tests unitaires BlockchainSyncQueueService.
 *
 * Cible :
 *   - enqueue cree une task PENDING avec payload JSON valide
 *   - markSuccess set txHash + status + clear next_attempt
 *   - markFailure incremente attempts + backoff progressif
 *   - markFailure apres 5 tentatives => FAILED definitif
 */
class BlockchainSyncQueueServiceTest {

    private BlockchainSyncTaskRepository repository;
    private BlockchainSyncQueueService service;

    @BeforeEach
    void setUp() {
        repository = mock(BlockchainSyncTaskRepository.class);
        service = new BlockchainSyncQueueService(repository);
        // Le service appelle save(...) puis returns la task : on simule un id auto-gen.
        when(repository.save(any())).thenAnswer(inv -> {
            BlockchainSyncTask t = inv.getArgument(0);
            if (t.getId() == null) t.setId(42L);
            return t;
        });
    }

    // =========================================================================
    // enqueue
    // =========================================================================

    @Test
    void enqueue_cree_task_PENDING_avec_payload_json() {
        Map<String, Object> payload = BlockchainSyncQueueService.payloadSyncPrix(
                "0xContract", 1112L, 325, 800, "DECLARATION_REVENU_VALIDEE", 42L);

        BlockchainSyncTask result = service.enqueue(
                TypeSyncBlockchain.SYNC_PRIX, 7L, payload);

        ArgumentCaptor<BlockchainSyncTask> cap = ArgumentCaptor.forClass(BlockchainSyncTask.class);
        verify(repository).save(cap.capture());
        BlockchainSyncTask saved = cap.getValue();

        assertThat(saved.getType()).isEqualTo(TypeSyncBlockchain.SYNC_PRIX);
        assertThat(saved.getRefId()).isEqualTo(7L);
        assertThat(saved.getStatus()).isEqualTo(StatutSyncBlockchain.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getNextAttemptAt()).isCloseTo(LocalDateTime.now(), within(2, ChronoUnit.SECONDS));
        // payload doit etre du JSON valide qui contient les bonnes cles
        assertThat(saved.getPayload()).contains("\"contractAddress\":\"0xContract\"");
        assertThat(saved.getPayload()).contains("\"prixCourant\":1112");
        assertThat(saved.getPayload()).contains("\"raison\":\"DECLARATION_REVENU_VALIDEE\"");
        assertThat(result).isSameAs(saved);
    }

    @Test
    void enqueue_accepte_refId_null() {
        Map<String, Object> payload = KycLedgerService.payloadEnregistrerKyc(
                "0xWallet", "0xHash", 1717804800L, 1749340800L);

        service.enqueue(TypeSyncBlockchain.ENREGISTRER_KYC, null, payload);

        ArgumentCaptor<BlockchainSyncTask> cap = ArgumentCaptor.forClass(BlockchainSyncTask.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getRefId()).isNull();
        assertThat(cap.getValue().getType()).isEqualTo(TypeSyncBlockchain.ENREGISTRER_KYC);
    }

    // =========================================================================
    // markSuccess
    // =========================================================================

    @Test
    void markSuccess_set_txHash_et_status_clean() {
        BlockchainSyncTask existing = taskExistante(StatutSyncBlockchain.PENDING, 2);
        existing.setLastError("old error");
        existing.setNextAttemptAt(LocalDateTime.now().plusMinutes(5));
        when(repository.findById(42L)).thenReturn(Optional.of(existing));

        service.markSuccess(42L, "0xabc...txhash");

        assertThat(existing.getStatus()).isEqualTo(StatutSyncBlockchain.SUCCESS);
        assertThat(existing.getTxHash()).isEqualTo("0xabc...txhash");
        assertThat(existing.getLastError()).isNull();
        assertThat(existing.getNextAttemptAt()).isNull();
        assertThat(existing.getLastAttemptAt()).isCloseTo(LocalDateTime.now(), within(2, ChronoUnit.SECONDS));
        verify(repository).save(existing);
    }

    @Test
    void markSuccess_silencieux_si_task_introuvable() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        service.markSuccess(99L, "0xtx"); // ne doit pas throw
        verify(repository, times(0)).save(any());
    }

    // =========================================================================
    // markFailure : backoff progressif
    // =========================================================================

    @Test
    void markFailure_essai_1_planifie_dans_1_minute() {
        BlockchainSyncTask t = taskExistante(StatutSyncBlockchain.PENDING, 0);
        when(repository.findById(42L)).thenReturn(Optional.of(t));

        service.markFailure(42L, "RPC timeout");

        assertThat(t.getAttempts()).isEqualTo(1);
        assertThat(t.getStatus()).isEqualTo(StatutSyncBlockchain.PENDING);
        assertThat(t.getLastError()).isEqualTo("RPC timeout");
        assertThat(t.getNextAttemptAt())
                .isCloseTo(LocalDateTime.now().plusMinutes(1), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void markFailure_essai_2_planifie_dans_5_minutes() {
        BlockchainSyncTask t = taskExistante(StatutSyncBlockchain.PENDING, 1);
        when(repository.findById(42L)).thenReturn(Optional.of(t));

        service.markFailure(42L, "err");

        assertThat(t.getAttempts()).isEqualTo(2);
        assertThat(t.getNextAttemptAt())
                .isCloseTo(LocalDateTime.now().plusMinutes(5), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void markFailure_essai_3_planifie_dans_15_minutes() {
        BlockchainSyncTask t = taskExistante(StatutSyncBlockchain.PENDING, 2);
        when(repository.findById(42L)).thenReturn(Optional.of(t));

        service.markFailure(42L, "err");

        assertThat(t.getNextAttemptAt())
                .isCloseTo(LocalDateTime.now().plusMinutes(15), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void markFailure_essai_4_planifie_dans_60_minutes() {
        BlockchainSyncTask t = taskExistante(StatutSyncBlockchain.PENDING, 3);
        when(repository.findById(42L)).thenReturn(Optional.of(t));

        service.markFailure(42L, "err");

        assertThat(t.getNextAttemptAt())
                .isCloseTo(LocalDateTime.now().plusMinutes(60), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void markFailure_essai_5_passe_en_FAILED_definitif() {
        BlockchainSyncTask t = taskExistante(StatutSyncBlockchain.PENDING, 4);
        when(repository.findById(42L)).thenReturn(Optional.of(t));

        service.markFailure(42L, "Abandon final");

        assertThat(t.getAttempts()).isEqualTo(5);
        assertThat(t.getStatus()).isEqualTo(StatutSyncBlockchain.FAILED);
        assertThat(t.getNextAttemptAt()).isNull();
        assertThat(t.getLastError()).isEqualTo("Abandon final");
    }

    @Test
    void markFailure_tronque_message_erreur_long() {
        BlockchainSyncTask t = taskExistante(StatutSyncBlockchain.PENDING, 0);
        when(repository.findById(42L)).thenReturn(Optional.of(t));

        String longMsg = "x".repeat(10_000);
        service.markFailure(42L, longMsg);

        assertThat(t.getLastError()).hasSize(4_000);
    }

    @Test
    void MAX_ATTEMPTS_est_5() {
        assertThat(BlockchainSyncQueueService.MAX_ATTEMPTS).isEqualTo(5);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private BlockchainSyncTask taskExistante(StatutSyncBlockchain status, int attempts) {
        BlockchainSyncTask t = new BlockchainSyncTask();
        t.setId(42L);
        t.setType(TypeSyncBlockchain.SYNC_PRIX);
        t.setStatus(status);
        t.setAttempts(attempts);
        t.setPayload("{}");
        t.setCreatedAt(LocalDateTime.now().minusHours(1));
        t.setUpdatedAt(LocalDateTime.now().minusHours(1));
        return t;
    }
}
