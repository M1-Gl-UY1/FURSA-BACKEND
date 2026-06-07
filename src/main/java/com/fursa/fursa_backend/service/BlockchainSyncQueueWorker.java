package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.BlockchainSyncTask;
import com.fursa.fursa_backend.model.enumeration.RaisonRecalculPrix;
import com.fursa.fursa_backend.model.enumeration.StatutSyncBlockchain;
import com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain;
import com.fursa.fursa_backend.repository.BlockchainSyncTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V2 R (07/06/2026) : worker scheduled qui consomme la queue
 * blockchain_sync_queue.
 *
 *   - Toutes les 60s, prend jusqu'a 20 jobs PENDING dont next_attempt_at est echu
 *   - Pour chaque job, decode le payload et appelle le service idoine
 *     (BlockchainSyncService ou RevenueLedgerService)
 *   - markSuccess + txHash si OK
 *   - markFailure + retry avec backoff exponentiel si KO
 *
 * Note : les services callers (Phase O + P) deviennent eux aussi des
 * "enqueueurs" : ils appellent directement le worker via une 1ere tentative
 * synchrone, et delegate ensuite a la queue en cas d'echec. Voir Phase R.5.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainSyncQueueWorker {

    private static final int BATCH_SIZE = 20;

    private final BlockchainSyncTaskRepository repository;
    private final BlockchainSyncQueueService queueService;
    private final BlockchainSyncService syncService;
    private final RevenueLedgerService ledgerService;
    private final KycLedgerService kycLedgerService;

    /**
     * Cron tres frequent (60s) : sur Sepolia / Polygon, le block time est de
     * 12s / 2s. Les retries doivent etre rapides pour profiter des fenetres de
     * RPC dispo. Pas de fixedDelay pour eviter le drift cumule sur de longues
     * durees d'execution.
     */
    @Scheduled(fixedRate = 60_000L)
    public void drainQueue() {
        List<BlockchainSyncTask> ready = repository.findReady(
                StatutSyncBlockchain.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, BATCH_SIZE));
        if (ready.isEmpty()) return;

        log.info("[SyncWorker] {} job(s) PENDING a executer", ready.size());
        for (BlockchainSyncTask task : ready) {
            try {
                executeOne(task);
            } catch (Exception e) {
                queueService.markFailure(task.getId(), e.getMessage());
            }
        }
    }

    private void executeOne(BlockchainSyncTask task) throws Exception {
        Map<String, Object> payload = queueService.deserializePayload(task.getPayload());
        TypeSyncBlockchain type = task.getType();

        switch (type) {
            case SYNC_PRIX -> {
                String contract = (String) payload.get("contractAddress");
                long prix = asLong(payload.get("prixCourant"));
                int renta = asInt(payload.get("bonusRentaBps"));
                int demande = asInt(payload.get("bonusDemandeBps"));
                String raisonStr = (String) payload.get("raison");
                Long sourceId = asNullableLong(payload.get("sourceId"));
                RaisonRecalculPrix raison = raisonStr == null
                        ? RaisonRecalculPrix.AJUSTEMENT_ADMIN
                        : RaisonRecalculPrix.valueOf(raisonStr);
                String tx = syncService.syncPrixDirect(
                        contract, BigInteger.valueOf(prix), renta, demande, raison, sourceId);
                queueService.markSuccess(task.getId(), tx);
            }
            case SET_STATUT -> {
                String contract = (String) payload.get("contractAddress");
                String statut = (String) payload.get("statut");
                String tx = syncService.setStatutDirect(contract, statut);
                queueService.markSuccess(task.getId(), tx);
            }
            case ENREGISTRER_REVENU -> {
                String contract = (String) payload.get("contractAddress");
                int trim = asInt(payload.get("trimestre"));
                long montant = asLong(payload.get("montantUsd"));
                long dateUnix = asLong(payload.get("dateValidationUnix"));
                String hash = (String) payload.get("hashJustificatif");
                long revenuId = asLong(payload.get("revenuId"));
                String tx = ledgerService.enregistrerRevenuDirect(
                        contract, trim, montant, dateUnix, hash, revenuId);
                queueService.markSuccess(task.getId(), tx);
            }
            case ENREGISTRER_DISTRIBUTION -> {
                String contract = (String) payload.get("contractAddress");
                long revenuId = asLong(payload.get("revenuId"));
                @SuppressWarnings("unchecked")
                Map<String, Object> distRaw = (Map<String, Object>) payload.get("distributions");
                Map<String, Long> dist = new HashMap<>();
                if (distRaw != null) {
                    distRaw.forEach((k, v) -> dist.put(k, asLong(v)));
                }
                String tx = ledgerService.enregistrerDistributionBatchDirect(
                        contract, revenuId, dist);
                queueService.markSuccess(task.getId(), tx);
            }
            case ENREGISTRER_KYC -> {
                String wallet = (String) payload.get("walletAdresse");
                String hashHex = (String) payload.get("hashHex");
                long dateValid = asLong(payload.get("dateValidationUnix"));
                long expireLe = asLong(payload.get("expireLeUnix"));
                String tx = kycLedgerService.enregistrerKycDirect(
                        wallet, hashHex, dateValid, expireLe);
                queueService.markSuccess(task.getId(), tx);
            }
            case REVOQUER_KYC -> {
                String wallet = (String) payload.get("walletAdresse");
                String motif = (String) payload.get("motif");
                String tx = kycLedgerService.revoquerKycDirect(wallet, motif);
                queueService.markSuccess(task.getId(), tx);
            }
        }
    }

    // ── Coercion helpers (Jackson decode tout en Number generic) ──────────

    private static long asLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private static int asInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }

    private static Long asNullableLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }
}
