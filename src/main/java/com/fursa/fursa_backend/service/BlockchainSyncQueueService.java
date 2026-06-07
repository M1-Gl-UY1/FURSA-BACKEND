package com.fursa.fursa_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fursa.fursa_backend.model.BlockchainSyncTask;
import com.fursa.fursa_backend.model.enumeration.StatutSyncBlockchain;
import com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain;
import com.fursa.fursa_backend.repository.BlockchainSyncTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * V2 R (07/06/2026) : queue persistante pour les operations on-chain.
 *
 * Au lieu de logger silencieusement les echecs (Phase O+P), les services
 * appellent maintenant {@link #enqueue} pour persister l'intention. Un worker
 * scheduled retente avec backoff exponentiel, max 5 essais avant FAILED.
 *
 * Le payload est un Map serialise en JSON :
 *  - SYNC_PRIX                : { proprieteId, contractAddress, prix, rentaBps, demandeBps, raison, sourceId }
 *  - SET_STATUT               : { proprieteId, contractAddress, statut }  // PUBLIEE/SUSPENDUE/RETIREE
 *  - ENREGISTRER_REVENU       : { contractAddress, trimestre, montantUsd, dateValidationUnix, hashJustificatif, revenuId }
 *  - ENREGISTRER_DISTRIBUTION : { contractAddress, revenuId, distributions: { wallet1: montant1, ... } }
 */
@Service
@Slf4j
public class BlockchainSyncQueueService {

    /** Backoff exponentiel par tentative : 1min, 5min, 15min, 1h, 6h. */
    private static final long[] BACKOFF_MINUTES = {1, 5, 15, 60, 360};
    public static final int MAX_ATTEMPTS = BACKOFF_MINUTES.length;

    private final BlockchainSyncTaskRepository repository;
    private final ObjectMapper objectMapper;

    public BlockchainSyncQueueService(BlockchainSyncTaskRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    // =========================================================================
    // Enqueue (utilise par les services callers en cas d'echec inline)
    // =========================================================================

    @Transactional
    public BlockchainSyncTask enqueue(TypeSyncBlockchain type, Long refId,
                                      Map<String, Object> payload) {
        try {
            BlockchainSyncTask task = new BlockchainSyncTask();
            task.setType(type);
            task.setRefId(refId);
            task.setPayload(objectMapper.writeValueAsString(payload));
            task.setStatus(StatutSyncBlockchain.PENDING);
            task.setAttempts(0);
            task.setNextAttemptAt(LocalDateTime.now()); // pret immediatement
            BlockchainSyncTask saved = repository.save(task);
            log.info("[SyncQueue] Enqueue type={} ref={} id={}", type, refId, saved.getId());
            return saved;
        } catch (Exception e) {
            // Cas tres exceptionnel (jackson cassé / DB down). On log et on
            // propage : si la queue ne marche pas, autant que ca remonte.
            log.error("[SyncQueue] Echec enqueue type={} ref={} : {}",
                    type, refId, e.getMessage(), e);
            throw new RuntimeException("Enqueue impossible", e);
        }
    }

    // =========================================================================
    // Marquage post-execution (utilise par le worker)
    // =========================================================================

    @Transactional
    public void markSuccess(Long taskId, String txHash) {
        repository.findById(taskId).ifPresent(t -> {
            t.setStatus(StatutSyncBlockchain.SUCCESS);
            t.setTxHash(txHash);
            t.setLastAttemptAt(LocalDateTime.now());
            t.setLastError(null);
            t.setNextAttemptAt(null);
            repository.save(t);
        });
    }

    @Transactional
    public void markFailure(Long taskId, String error) {
        repository.findById(taskId).ifPresent(t -> {
            int attempts = (t.getAttempts() == null ? 0 : t.getAttempts()) + 1;
            t.setAttempts(attempts);
            t.setLastAttemptAt(LocalDateTime.now());
            t.setLastError(truncate(error, 4000));

            if (attempts >= MAX_ATTEMPTS) {
                t.setStatus(StatutSyncBlockchain.FAILED);
                t.setNextAttemptAt(null);
                log.error("[SyncQueue] Abandon task={} apres {} essais : {}",
                        taskId, attempts, error);
            } else {
                long delay = BACKOFF_MINUTES[Math.min(attempts - 1, BACKOFF_MINUTES.length - 1)];
                t.setNextAttemptAt(LocalDateTime.now().plusMinutes(delay));
                log.warn("[SyncQueue] Retry task={} dans {}min (essai {}/{})",
                        taskId, delay, attempts, MAX_ATTEMPTS);
            }
            repository.save(t);
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    public Map<String, Object> deserializePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("[SyncQueue] Payload illisible : {}", e.getMessage());
            return Map.of();
        }
    }

    /** Helper pour construire un payload SYNC_PRIX. */
    public static Map<String, Object> payloadSyncPrix(
            String contractAddress, long prixCourant, int bonusRentaBps,
            int bonusDemandeBps, String raison, Long sourceId) {
        Map<String, Object> m = new HashMap<>();
        m.put("contractAddress", contractAddress);
        m.put("prixCourant", prixCourant);
        m.put("bonusRentaBps", bonusRentaBps);
        m.put("bonusDemandeBps", bonusDemandeBps);
        m.put("raison", raison);
        m.put("sourceId", sourceId);
        return m;
    }

    public static Map<String, Object> payloadSetStatut(String contractAddress, String statut) {
        Map<String, Object> m = new HashMap<>();
        m.put("contractAddress", contractAddress);
        m.put("statut", statut);
        return m;
    }

    public static Map<String, Object> payloadEnregistrerRevenu(
            String contractAddress, int trimestre, long montantUsd,
            long dateValidationUnix, String hashJustificatifHex, long revenuId) {
        Map<String, Object> m = new HashMap<>();
        m.put("contractAddress", contractAddress);
        m.put("trimestre", trimestre);
        m.put("montantUsd", montantUsd);
        m.put("dateValidationUnix", dateValidationUnix);
        m.put("hashJustificatif", hashJustificatifHex);
        m.put("revenuId", revenuId);
        return m;
    }

    public static Map<String, Object> payloadEnregistrerDistribution(
            String contractAddress, long revenuId, Map<String, Long> distributions) {
        Map<String, Object> m = new HashMap<>();
        m.put("contractAddress", contractAddress);
        m.put("revenuId", revenuId);
        m.put("distributions", distributions);
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
