package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.RaisonRecalculPrix;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;

/**
 * V2 O (07/06/2026) : pousse les changements BDD vers les contrats ProprieteTokenV2.
 *
 * Appele depuis :
 *   - PrixPartService.recalculer() apres MAJ BDD (push prix + bonus)
 *   - ProprieteService quand le statut admin bascule (push statut)
 *
 * Toutes les methodes sont @Async (thread separe) : pas de blocage du flow
 * applicatif. En cas d'echec, on logue + on incremente un compteur. Pas de
 * retry automatique (chantier P3 : worker dedie avec queue persistante).
 *
 * Idempotence : la chain n'a pas de notion d'idempotency-key, mais comme on
 * envoie un setter (pas un increment), un double-call ecrit deux fois la
 * meme valeur — sans effet de bord.
 */
@Service
@Slf4j
public class BlockchainSyncService {

    private final BlockchainRpcClient blockchainRpcClient;
    private final Credentials credentials;
    private final long chainId;
    private final long gasPrice;
    private final long gasLimit;

    public BlockchainSyncService(
            BlockchainRpcClient blockchainRpcClient,
            Credentials credentials,
            @Value("${blockchain.chain-id}") long chainId,
            @Value("${blockchain.gas-price:20000000000}") long gasPrice,
            // setter = transaction simple ~50k gas, on cape a 200k pour marge.
            @Value("${blockchain.sync-gas-limit:200000}") long gasLimit) {
        this.blockchainRpcClient = blockchainRpcClient;
        this.credentials = credentials;
        this.chainId = chainId;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        log.info("BlockchainSyncService initialise : chainId={} gasPrice={} Gwei gasLimit={}",
                chainId, gasPrice / 1_000_000_000L, gasLimit);
    }

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Pousse le nouveau prix courant + les deux bonus on-chain. Async : ne
     * bloque pas le caller. Ignore si le bien n'est pas un V2 ou pas tokenise.
     *
     * @param prixCourantUsd   en unite entiere USD (ex: 1112 pour 1112.00)
     * @param bonusRentaBps    en bps signes (ex: 325 = +3.25%)
     * @param bonusDemandeBps  en bps non signes (ex: 800 = +8%)
     */
    @Async
    public void pushPrixCourant(
            Propriete propriete,
            BigInteger prixCourantUsd,
            int bonusRentaBps,
            int bonusDemandeBps,
            RaisonRecalculPrix raison,
            Long sourceId) {

        if (!estEligibleV2(propriete)) {
            log.debug("[BlockchainSync] Skip push prix prop={} (non eligible V2)", propriete.getId());
            return;
        }

        try {
            int raisonCode = mapRaisonToOnchainCode(raison);
            long sourceIdSafe = sourceId == null ? 0L : sourceId;

            Function fn = new Function(
                    "syncPrix",
                    List.of(
                            new Uint256(prixCourantUsd),
                            new Int256(BigInteger.valueOf(bonusRentaBps)),
                            new Uint256(BigInteger.valueOf(bonusDemandeBps)),
                            new Uint8(BigInteger.valueOf(raisonCode)),
                            new Uint256(BigInteger.valueOf(sourceIdSafe))
                    ),
                    java.util.Collections.<org.web3j.abi.TypeReference<?>>emptyList()
            );

            String txHash = broadcast(propriete.getAdresseContrat(), FunctionEncoder.encode(fn));
            log.info("[BlockchainSync] syncPrix prop={} contrat={} prix={} renta={}bps dem={}bps tx={}",
                    propriete.getId(), propriete.getAdresseContrat(),
                    prixCourantUsd, bonusRentaBps, bonusDemandeBps, txHash);

        } catch (Exception e) {
            log.error("[BlockchainSync] Echec push prix prop={} : {}",
                    propriete.getId(), e.getMessage(), e);
        }
    }

    /**
     * Pousse le nouveau statut on-chain (mapping Statut java -> uint8 enum solidity).
     * Async : ne bloque pas le caller.
     */
    @Async
    public void pushStatut(Propriete propriete, StatutPropriete nouveauStatut) {
        if (!estEligibleV2(propriete)) {
            log.debug("[BlockchainSync] Skip push statut prop={} (non eligible V2)", propriete.getId());
            return;
        }

        int statutOnchain = mapStatutToOnchainCode(nouveauStatut);
        if (statutOnchain < 0) {
            // Statuts non publics on-chain (BROUILLON, ACCEPTEE, REFUSEE, EN_TOKENISATION...)
            log.debug("[BlockchainSync] Statut {} non reflete on-chain pour prop={}",
                    nouveauStatut, propriete.getId());
            return;
        }

        try {
            Function fn = new Function(
                    "setStatut",
                    List.<Type>of(new Uint8(BigInteger.valueOf(statutOnchain))),
                    java.util.Collections.<org.web3j.abi.TypeReference<?>>emptyList()
            );
            String txHash = broadcast(propriete.getAdresseContrat(), FunctionEncoder.encode(fn));
            log.info("[BlockchainSync] setStatut prop={} contrat={} statut={} -> code={} tx={}",
                    propriete.getId(), propriete.getAdresseContrat(),
                    nouveauStatut, statutOnchain, txHash);

        } catch (Exception e) {
            log.error("[BlockchainSync] Echec push statut prop={} : {}",
                    propriete.getId(), e.getMessage(), e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean estEligibleV2(Propriete p) {
        if (p == null) return false;
        if (!"V2".equalsIgnoreCase(p.getContratVersion())) return false;
        String addr = p.getAdresseContrat();
        return addr != null && !addr.isBlank();
    }

    /** Mapping RaisonRecalculPrix -> uint8 conformement au contrat V2. */
    private int mapRaisonToOnchainCode(RaisonRecalculPrix r) {
        if (r == null) return 4; // AJUSTEMENT_ADMIN par defaut
        return switch (r) {
            case DECLARATION_REVENU_VALIDEE -> 1;
            case LISTE_ATTENTE_CHANGEE      -> 2;
            case CRON_TRIMESTRIEL           -> 3;
            case AJUSTEMENT_ADMIN, TRADE_SECONDAIRE, INITIALE -> 4;
        };
    }

    /**
     * Mapping StatutPropriete (java) -> uint8 (solidity enum Statut).
     *  0 = PUBLIEE, 1 = SUSPENDUE, 2 = RETIREE.
     *
     * Phase O actuelle : seul PUBLIEE existe cote java. Les statuts SUSPENDUE
     * et RETIREE seront ajoutes a l'enum java dans un chantier ulterieur quand
     * le besoin metier sera valide. En attendant, REJETEE et REFUSEE sont
     * mappes vers RETIREE on-chain (le bien a ete sorti du catalogue public).
     * Retourne -1 pour les statuts qui ne refletent pas un etat publie.
     */
    private int mapStatutToOnchainCode(StatutPropriete s) {
        if (s == null) return -1;
        return switch (s) {
            case PUBLIEE              -> 0;
            case REJETEE, REFUSEE     -> 2;
            default                   -> -1;  // BROUILLON, EN_REVIEW, ACCEPTEE, EN_TOKENISATION, EN_ATTENTE
        };
    }

    /**
     * Construit, signe et broadcast une tx d'appel de fonction.
     * Renvoie le txHash. Ne lit pas le receipt (la confirmation arrive plus
     * tard, on accepte le decalage temporaire — la BDD est de toute facon source
     * de verite cote plateforme).
     */
    private String broadcast(String contractAddress, String encodedData) throws Exception {
        String nonceHex = blockchainRpcClient.getNonce(credentials.getAddress());
        BigInteger nonce = Numeric.decodeQuantity(nonceHex);

        RawTransaction tx = RawTransaction.createTransaction(
                nonce,
                BigInteger.valueOf(gasPrice),
                BigInteger.valueOf(gasLimit),
                contractAddress,
                BigInteger.ZERO,
                encodedData
        );
        byte[] signed = TransactionEncoder.signMessage(tx, chainId, credentials);
        String hex = Numeric.toHexString(signed);

        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\","
                + "\"params\":[\"" + hex + "\"],\"id\":1}";
        var response = blockchainRpcClient.sendRpc(body);
        String responseBody = response.body();

        if (!responseBody.contains("\"result\":\"")) {
            throw new RuntimeException("Erreur broadcast tx : " + responseBody);
        }
        return responseBody.split("\"result\":\"")[1].split("\"")[0];
    }
}
