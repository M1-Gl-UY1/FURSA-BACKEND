package com.fursa.fursa_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * V2 P (07/06/2026) : pousse les revenus valides + distributions de dividendes
 * vers le contrat singleton RevenueLedger pour creer un audit trail on-chain.
 *
 * Le contrat n'est PAS source de verite (la BDD reste autoritative) :
 *   - source de verite des dividendes : table dividende
 *   - source de verite des revenus    : table revenus
 *   - source de verite du justificatif : fichier sur disque + URL en BDD
 *
 * Le ledger sert a :
 *   1. prouver qu'a un instant T, un revenu de X USD existait, justifie par un
 *      PDF dont le hash est sur la chain ;
 *   2. permettre a chaque investisseur de verifier independamment de FURSA le
 *      cumul des dividendes qu'il a percus par propriete.
 *
 * Tous les appels sont async : pas de blocage du flow applicatif. Si la
 * variable revenue-ledger-address est vide (cas dev local sans deploiement),
 * les methodes sont des no-op silencieux.
 */
@Service
@Slf4j
public class RevenueLedgerService {

    private final BlockchainRpcClient blockchainRpcClient;
    private final Credentials credentials;
    private final long chainId;
    private final long gasPrice;
    private final long gasLimit;
    private final String ledgerAddress;
    private final BlockchainSyncQueueService queueService;

    public RevenueLedgerService(
            BlockchainRpcClient blockchainRpcClient,
            Credentials credentials,
            @Value("${blockchain.chain-id}") long chainId,
            @Value("${blockchain.gas-price:20000000000}") long gasPrice,
            // enregistrerRevenu ~160k gas, batch dist peut depasser 500k selon taille.
            @Value("${blockchain.ledger-gas-limit:1500000}") long gasLimit,
            @Value("${blockchain.revenue-ledger-address:}") String ledgerAddress,
            BlockchainSyncQueueService queueService) {
        this.blockchainRpcClient = blockchainRpcClient;
        this.credentials = credentials;
        this.chainId = chainId;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.ledgerAddress = ledgerAddress == null ? "" : ledgerAddress.trim();
        this.queueService = queueService;
        if (this.ledgerAddress.isEmpty()) {
            log.warn("[Ledger] revenue-ledger-address vide : no-op (pas de push on-chain).");
        } else {
            log.info("[Ledger] RevenueLedgerService initialise : ledger={} gasPrice={} Gwei",
                    this.ledgerAddress, gasPrice / 1_000_000_000L);
        }
    }

    public boolean estActif() {
        return !ledgerAddress.isEmpty();
    }

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Enregistre un revenu valide on-chain.
     *
     * @param proprieteTokenAddress adresse du ProprieteTokenV2 (null/blank → no-op)
     * @param trimestre format YYYYQ (ex: 20262 pour Q2 2026)
     * @param montantUsd montant net en USD entier
     * @param dateValidationUnix timestamp UNIX en secondes
     * @param hashJustificatifHex 0x... 32 bytes (sha256 du PDF), null autorise → 0x000…
     * @param revenuIdBackend id du revenu en BDD
     */
    @Async
    public void enregistrerRevenu(
            String proprieteTokenAddress,
            int trimestre,
            long montantUsd,
            long dateValidationUnix,
            String hashJustificatifHex,
            long revenuIdBackend) {
        if (!estEligible(proprieteTokenAddress)) return;
        try {
            String txHash = enregistrerRevenuDirect(
                    proprieteTokenAddress, trimestre, montantUsd,
                    dateValidationUnix, hashJustificatifHex, revenuIdBackend);
            log.info("[Ledger] enregistrerRevenu prop={} trim={} montant={} revenuId={} tx={}",
                    proprieteTokenAddress, trimestre, montantUsd, revenuIdBackend, txHash);
        } catch (Exception e) {
            log.warn("[Ledger] Echec enregistrerRevenu revenuId={} → enqueue : {}",
                    revenuIdBackend, e.getMessage());
            queueService.enqueue(
                    com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain.ENREGISTRER_REVENU,
                    revenuIdBackend,
                    BlockchainSyncQueueService.payloadEnregistrerRevenu(
                            proprieteTokenAddress, trimestre, montantUsd,
                            dateValidationUnix, hashJustificatifHex, revenuIdBackend));
        }
    }

    /** V2 R : variante synchrone (worker queue). Throw au lieu de log silencieux. */
    public String enregistrerRevenuDirect(
            String proprieteTokenAddress,
            int trimestre,
            long montantUsd,
            long dateValidationUnix,
            String hashJustificatifHex,
            long revenuIdBackend) throws Exception {
        byte[] hashBytes = parseBytes32OrZero(hashJustificatifHex);
        Function fn = new Function(
                "enregistrerRevenu",
                List.<Type>of(
                        new Address(proprieteTokenAddress),
                        new Uint32(BigInteger.valueOf(trimestre)),
                        new Uint256(BigInteger.valueOf(montantUsd)),
                        new Uint64(BigInteger.valueOf(dateValidationUnix)),
                        new Bytes32(hashBytes),
                        new Uint256(BigInteger.valueOf(revenuIdBackend))
                ),
                java.util.Collections.<org.web3j.abi.TypeReference<?>>emptyList()
        );
        return broadcast(FunctionEncoder.encode(fn));
    }

    /**
     * Enregistre la distribution complete d'un revenu en mode batch (une seule
     * tx pour tous les investisseurs). Plus economique en gas que N appels
     * unitaires si la liste a plus de 2 entrees.
     *
     * @param distributions mapping adresse_investisseur → montant_usd
     */
    @Async
    public void enregistrerDistributionBatch(
            String proprieteTokenAddress,
            long revenuIdBackend,
            Map<String, Long> distributions) {
        if (!estEligible(proprieteTokenAddress)) return;
        if (distributions == null || distributions.isEmpty()) {
            log.debug("[Ledger] Skip distribution batch revenuId={} (vide)", revenuIdBackend);
            return;
        }
        try {
            String txHash = enregistrerDistributionBatchDirect(
                    proprieteTokenAddress, revenuIdBackend, distributions);
            log.info("[Ledger] distributionBatch prop={} revenuId={} n={} tx={}",
                    proprieteTokenAddress, revenuIdBackend, distributions.size(), txHash);
        } catch (Exception e) {
            log.warn("[Ledger] Echec distributionBatch revenuId={} → enqueue : {}",
                    revenuIdBackend, e.getMessage());
            queueService.enqueue(
                    com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain.ENREGISTRER_DISTRIBUTION,
                    revenuIdBackend,
                    BlockchainSyncQueueService.payloadEnregistrerDistribution(
                            proprieteTokenAddress, revenuIdBackend, distributions));
        }
    }

    /** V2 R : variante synchrone (worker queue). Throw au lieu de log silencieux. */
    public String enregistrerDistributionBatchDirect(
            String proprieteTokenAddress,
            long revenuIdBackend,
            Map<String, Long> distributions) throws Exception {
        List<Address> investisseurs = distributions.keySet().stream()
                .map(Address::new).toList();
        List<Uint256> montants = distributions.values().stream()
                .map(m -> new Uint256(BigInteger.valueOf(m))).toList();

        Function fn = new Function(
                "enregistrerDistributionBatch",
                List.<Type>of(
                        new Address(proprieteTokenAddress),
                        new Uint256(BigInteger.valueOf(revenuIdBackend)),
                        new DynamicArray<>(Address.class, investisseurs),
                        new DynamicArray<>(Uint256.class, montants)
                ),
                java.util.Collections.<org.web3j.abi.TypeReference<?>>emptyList()
        );
        return broadcast(FunctionEncoder.encode(fn));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Calcule le sha256 d'un tableau de bytes et le retourne en hex 0x... 64 chars.
     * Utilise par les callers pour ancrer le justificatif d'un revenu.
     */
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(data);
            return "0x" + Numeric.toHexStringNoPrefix(h);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 indisponible", e);
        }
    }

    /** Format trimestre YYYYQ : 20262 = Q2 2026, 20264 = Q4 2026. */
    public static int trimestreCode(int annee, int quarter) {
        if (quarter < 1 || quarter > 4) throw new IllegalArgumentException("quarter 1..4");
        return annee * 10 + quarter;
    }

    private boolean estEligible(String proprieteAddress) {
        if (!estActif()) {
            log.debug("[Ledger] Skip : ledger non configure");
            return false;
        }
        if (proprieteAddress == null || proprieteAddress.isBlank()) {
            log.debug("[Ledger] Skip : adresse propriete vide (bien non tokenise)");
            return false;
        }
        return true;
    }

    private byte[] parseBytes32OrZero(String hex) {
        if (hex == null || hex.isBlank()) return new byte[32];
        String clean = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (clean.length() != 64) {
            log.warn("[Ledger] hash justif longueur inattendue ({}), zero substitue", clean.length());
            return new byte[32];
        }
        return Numeric.hexStringToByteArray(clean);
    }

    private String broadcast(String encodedData) throws Exception {
        String nonceHex = blockchainRpcClient.getNonce(credentials.getAddress());
        BigInteger nonce = Numeric.decodeQuantity(nonceHex);

        RawTransaction tx = RawTransaction.createTransaction(
                nonce,
                BigInteger.valueOf(gasPrice),
                BigInteger.valueOf(gasLimit),
                ledgerAddress,
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
            throw new RuntimeException("Erreur broadcast Ledger : " + responseBody);
        }
        return responseBody.split("\"result\":\"")[1].split("\"")[0];
    }
}
