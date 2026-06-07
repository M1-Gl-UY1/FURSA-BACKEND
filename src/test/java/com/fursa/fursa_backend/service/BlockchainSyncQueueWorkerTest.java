package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.BlockchainSyncTask;
import com.fursa.fursa_backend.model.enumeration.RaisonRecalculPrix;
import com.fursa.fursa_backend.model.enumeration.StatutSyncBlockchain;
import com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain;
import com.fursa.fursa_backend.repository.BlockchainSyncTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2 U (07/06/2026) : tests unitaires BlockchainSyncQueueWorker.
 *
 * Cible :
 *   - drainQueue n'agit pas si queue vide
 *   - dispatch correct des 6 types de tache vers les bons services
 *   - markSuccess appele quand l'execution renvoie un txHash
 *   - markFailure appele quand l'execution throw
 *   - une tache qui throw ne casse pas le batch (isolation)
 */
class BlockchainSyncQueueWorkerTest {

    private BlockchainSyncTaskRepository repository;
    private BlockchainSyncQueueService queueService;
    private BlockchainSyncService syncService;
    private RevenueLedgerService ledgerService;
    private KycLedgerService kycLedgerService;
    private BlockchainSyncQueueWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(BlockchainSyncTaskRepository.class);
        queueService = mock(BlockchainSyncQueueService.class);
        syncService = mock(BlockchainSyncService.class);
        ledgerService = mock(RevenueLedgerService.class);
        kycLedgerService = mock(KycLedgerService.class);
        worker = new BlockchainSyncQueueWorker(
                repository, queueService, syncService, ledgerService, kycLedgerService);
    }

    // =========================================================================
    // Pas de boulot = pas d'action
    // =========================================================================

    @Test
    void drainQueue_no_op_si_queue_vide() {
        when(repository.findReady(any(), any(), any())).thenReturn(Collections.emptyList());

        worker.drainQueue();

        verify(queueService, never()).markSuccess(anyLong(), anyString());
        verify(queueService, never()).markFailure(anyLong(), anyString());
    }

    // =========================================================================
    // Dispatch des 6 types
    // =========================================================================

    @Test
    void dispatch_SYNC_PRIX_vers_syncService() throws Exception {
        BlockchainSyncTask task = tache(1L, TypeSyncBlockchain.SYNC_PRIX, Map.of(
                "contractAddress", "0xContract",
                "prixCourant", 1112,
                "bonusRentaBps", 325,
                "bonusDemandeBps", 800,
                "raison", "DECLARATION_REVENU_VALIDEE",
                "sourceId", 42
        ));
        when(repository.findReady(any(), any(), any())).thenReturn(List.of(task));
        when(syncService.syncPrixDirect(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn("0xTxHashPrix");

        worker.drainQueue();

        verify(syncService).syncPrixDirect(
                eq("0xContract"),
                eq(BigInteger.valueOf(1112L)),
                eq(325),
                eq(800),
                eq(RaisonRecalculPrix.DECLARATION_REVENU_VALIDEE),
                eq(42L));
        verify(queueService).markSuccess(1L, "0xTxHashPrix");
    }

    @Test
    void dispatch_SET_STATUT_vers_syncService() throws Exception {
        BlockchainSyncTask task = tache(2L, TypeSyncBlockchain.SET_STATUT, Map.of(
                "contractAddress", "0xContract",
                "statut", "PUBLIEE"
        ));
        when(repository.findReady(any(), any(), any())).thenReturn(List.of(task));
        when(syncService.setStatutDirect(any(), any())).thenReturn("0xTxHashStatut");

        worker.drainQueue();

        verify(syncService).setStatutDirect("0xContract", "PUBLIEE");
        verify(queueService).markSuccess(2L, "0xTxHashStatut");
    }

    @Test
    void dispatch_ENREGISTRER_REVENU_vers_ledgerService() throws Exception {
        BlockchainSyncTask task = tache(3L, TypeSyncBlockchain.ENREGISTRER_REVENU, Map.of(
                "contractAddress", "0xContract",
                "trimestre", 20262,
                "montantUsd", 5000,
                "dateValidationUnix", 1717804800L,
                "hashJustificatif", "0x" + "ab".repeat(32),
                "revenuId", 42
        ));
        when(repository.findReady(any(), any(), any())).thenReturn(List.of(task));
        when(ledgerService.enregistrerRevenuDirect(any(), anyInt(), anyLong(), anyLong(), any(), anyLong()))
                .thenReturn("0xTxHashRevenu");

        worker.drainQueue();

        verify(ledgerService).enregistrerRevenuDirect(
                eq("0xContract"), eq(20262), eq(5000L), eq(1717804800L),
                eq("0x" + "ab".repeat(32)), eq(42L));
        verify(queueService).markSuccess(3L, "0xTxHashRevenu");
    }

    @Test
    void dispatch_ENREGISTRER_DISTRIBUTION_vers_ledgerService() throws Exception {
        BlockchainSyncTask task = tache(4L, TypeSyncBlockchain.ENREGISTRER_DISTRIBUTION, Map.of(
                "contractAddress", "0xContract",
                "revenuId", 42,
                "distributions", Map.of(
                        "0xInv1", 800,
                        "0xInv2", 3000)
        ));
        when(repository.findReady(any(), any(), any())).thenReturn(List.of(task));
        when(ledgerService.enregistrerDistributionBatchDirect(any(), anyLong(), any()))
                .thenReturn("0xTxHashDistrib");

        worker.drainQueue();

        ArgumentCaptor<Map<String, Long>> mapCap = ArgumentCaptor.forClass(Map.class);
        verify(ledgerService).enregistrerDistributionBatchDirect(
                eq("0xContract"), eq(42L), mapCap.capture());
        assertThat(mapCap.getValue()).containsEntry("0xInv1", 800L);
        assertThat(mapCap.getValue()).containsEntry("0xInv2", 3000L);
        verify(queueService).markSuccess(4L, "0xTxHashDistrib");
    }

    @Test
    void dispatch_ENREGISTRER_KYC_vers_kycLedgerService() throws Exception {
        BlockchainSyncTask task = tache(5L, TypeSyncBlockchain.ENREGISTRER_KYC, Map.of(
                "walletAdresse", "0xWalletInv",
                "hashHex", "0x" + "cd".repeat(32),
                "dateValidationUnix", 1717804800L,
                "expireLeUnix", 1749340800L
        ));
        when(repository.findReady(any(), any(), any())).thenReturn(List.of(task));
        when(kycLedgerService.enregistrerKycDirect(any(), any(), anyLong(), anyLong()))
                .thenReturn("0xTxHashKyc");

        worker.drainQueue();

        verify(kycLedgerService).enregistrerKycDirect(
                eq("0xWalletInv"),
                eq("0x" + "cd".repeat(32)),
                eq(1717804800L),
                eq(1749340800L));
        verify(queueService).markSuccess(5L, "0xTxHashKyc");
    }

    @Test
    void dispatch_REVOQUER_KYC_vers_kycLedgerService() throws Exception {
        BlockchainSyncTask task = tache(6L, TypeSyncBlockchain.REVOQUER_KYC, Map.of(
                "walletAdresse", "0xWalletInv",
                "motif", "Fraude detectee"
        ));
        when(repository.findReady(any(), any(), any())).thenReturn(List.of(task));
        when(kycLedgerService.revoquerKycDirect(any(), any())).thenReturn("0xTxHashRevoc");

        worker.drainQueue();

        verify(kycLedgerService).revoquerKycDirect("0xWalletInv", "Fraude detectee");
        verify(queueService).markSuccess(6L, "0xTxHashRevoc");
    }

    // =========================================================================
    // Gestion des erreurs
    // =========================================================================

    @Test
    void markFailure_appele_si_execution_throw() throws Exception {
        BlockchainSyncTask task = tache(7L, TypeSyncBlockchain.SET_STATUT, Map.of(
                "contractAddress", "0xContract",
                "statut", "PUBLIEE"
        ));
        when(repository.findReady(any(), any(), any())).thenReturn(List.of(task));
        when(syncService.setStatutDirect(any(), any()))
                .thenThrow(new RuntimeException("RPC down"));

        worker.drainQueue();

        verify(queueService).markFailure(7L, "RPC down");
        verify(queueService, never()).markSuccess(anyLong(), anyString());
    }

    @Test
    void une_tache_qui_throw_ne_casse_pas_le_batch() throws Exception {
        BlockchainSyncTask t1 = tache(10L, TypeSyncBlockchain.SET_STATUT, Map.of(
                "contractAddress", "0xC1", "statut", "PUBLIEE"));
        BlockchainSyncTask t2 = tache(11L, TypeSyncBlockchain.SET_STATUT, Map.of(
                "contractAddress", "0xC2", "statut", "PUBLIEE"));

        when(repository.findReady(any(), any(), any())).thenReturn(List.of(t1, t2));
        when(syncService.setStatutDirect(eq("0xC1"), any()))
                .thenThrow(new RuntimeException("boom"));
        when(syncService.setStatutDirect(eq("0xC2"), any()))
                .thenReturn("0xTxOK");

        worker.drainQueue();

        verify(queueService).markFailure(10L, "boom");
        verify(queueService).markSuccess(11L, "0xTxOK");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private BlockchainSyncTask tache(long id, TypeSyncBlockchain type, Map<String, Object> payload) {
        BlockchainSyncTask t = new BlockchainSyncTask();
        t.setId(id);
        t.setType(type);
        t.setStatus(StatutSyncBlockchain.PENDING);
        t.setAttempts(0);
        // Sérialise en JSON pour que deserializePayload reconstitue la map
        try {
            t.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Le worker appelle queueService.deserializePayload qui parse le JSON.
        // On stub le retour pour ne pas depender d'une vraie deserialisation.
        when(queueService.deserializePayload(t.getPayload())).thenReturn(payload);
        return t;
    }
}
