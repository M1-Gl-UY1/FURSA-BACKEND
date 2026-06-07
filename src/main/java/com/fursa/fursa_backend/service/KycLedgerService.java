package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V2 T (07/06/2026) : ancrage on-chain RGPD-safe des KYC valides.
 *
 *   Le contrat KycRegistry stocke uniquement le hash keccak256 du dossier KYC
 *   (concat avec un sel secret backend) + statut + dates. Aucune donnee perso
 *   n'est jamais inscrite on-chain.
 *
 *   Tous les appels sont @Async : pas de blocage du flow applicatif. Si echec
 *   inline, on enqueue dans blockchain_sync_queue pour retry async.
 *
 *   Si BLOCKCHAIN_KYC_REGISTRY_ADDRESS est vide, le service est no-op.
 */
@Service
@Slf4j
public class KycLedgerService {

    private final BlockchainRpcClient rpc;
    private final Credentials credentials;
    private final long chainId;
    private final long gasPrice;
    private final long gasLimit;
    private final String kycRegistryAddress;
    private final String salt;
    private final int dureeValiditeJours;
    private final BlockchainSyncQueueService queueService;

    public KycLedgerService(
            BlockchainRpcClient rpc,
            Credentials credentials,
            @Value("${blockchain.chain-id}") long chainId,
            @Value("${blockchain.gas-price:20000000000}") long gasPrice,
            // enregistrerKyc ~72k, revoquerKyc ~33k. Cap a 200k pour marge.
            @Value("${blockchain.kyc-gas-limit:200000}") long gasLimit,
            @Value("${blockchain.kyc-registry-address:}") String kycRegistryAddress,
            @Value("${app.kyc-onchain.salt:dev-only-salt-min-32-chars-change-in-prod-please}") String salt,
            @Value("${app.kyc-onchain.duree-validite-jours:365}") int dureeValiditeJours,
            BlockchainSyncQueueService queueService) {
        this.rpc = rpc;
        this.credentials = credentials;
        this.chainId = chainId;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.kycRegistryAddress = kycRegistryAddress == null ? "" : kycRegistryAddress.trim();
        this.salt = salt == null ? "" : salt;
        this.dureeValiditeJours = dureeValiditeJours;
        this.queueService = queueService;
        if (this.kycRegistryAddress.isEmpty()) {
            log.warn("[KycLedger] kyc-registry-address vide : no-op (pas de push on-chain).");
        } else {
            log.info("[KycLedger] KycLedgerService initialise : registry={} dureeValidite={}j",
                    this.kycRegistryAddress, dureeValiditeJours);
            if (this.salt.length() < 32) {
                log.error("[KycLedger] SALT TROP COURT ({} chars). RGPD/securite compromise.",
                        this.salt.length());
            }
        }
    }

    public boolean estActif() {
        return !kycRegistryAddress.isEmpty();
    }

    // =========================================================================
    // API publique (async, enqueue si echec)
    // =========================================================================

    /**
     * Calcule le hash KYC + push on-chain. dateValidation = aujourd'hui,
     * expireLe = aujourd'hui + dureeValiditeJours.
     *
     * @param walletAdresse  adresse wallet de l'investisseur (must be 0x... 40 chars hex)
     * @param prenom         champ KYC
     * @param nom            champ KYC
     * @param dateNaissance  champ KYC
     * @param numeroPiece    champ KYC (CNI, passport, etc.)
     */
    @Async
    public void enregistrerKyc(String walletAdresse, String prenom, String nom,
                                LocalDate dateNaissance, String numeroPiece) {
        if (!estEligible(walletAdresse)) return;
        String hashHex = hashKyc(prenom, nom, dateNaissance, numeroPiece);
        long now = java.time.Instant.now().getEpochSecond();
        long expireLe = now + (long) dureeValiditeJours * 86400L;
        try {
            String tx = enregistrerKycDirect(walletAdresse, hashHex, now, expireLe);
            log.info("[KycLedger] enregistrerKyc wallet={} expireLe={} tx={}",
                    walletAdresse, expireLe, tx);
        } catch (Exception e) {
            log.warn("[KycLedger] Echec enregistrerKyc wallet={} → enqueue : {}",
                    walletAdresse, e.getMessage());
            queueService.enqueue(
                    TypeSyncBlockchain.ENREGISTRER_KYC,
                    null,  // pas de refId tabulaire (le wallet n'est pas un long)
                    payloadEnregistrerKyc(walletAdresse, hashHex, now, expireLe));
        }
    }

    @Async
    public void revoquerKyc(String walletAdresse, String motif) {
        if (!estEligible(walletAdresse)) return;
        String motifSafe = motif == null || motif.isBlank() ? "Sans motif" : motif;
        try {
            String tx = revoquerKycDirect(walletAdresse, motifSafe);
            log.info("[KycLedger] revoquerKyc wallet={} motif='{}' tx={}",
                    walletAdresse, motifSafe, tx);
        } catch (Exception e) {
            log.warn("[KycLedger] Echec revoquerKyc wallet={} → enqueue : {}",
                    walletAdresse, e.getMessage());
            queueService.enqueue(
                    TypeSyncBlockchain.REVOQUER_KYC,
                    null,
                    payloadRevoquerKyc(walletAdresse, motifSafe));
        }
    }

    // =========================================================================
    // Variantes synchrones (utilisees par le worker queue)
    // =========================================================================

    public String enregistrerKycDirect(String walletAdresse, String hashHex,
                                        long dateValidationUnix, long expireLeUnix)
            throws Exception {
        byte[] hashBytes = parseBytes32(hashHex);
        Function fn = new Function(
                "enregistrerKyc",
                List.<Type>of(
                        new Address(walletAdresse),
                        new Bytes32(hashBytes),
                        new Uint64(BigInteger.valueOf(dateValidationUnix)),
                        new Uint64(BigInteger.valueOf(expireLeUnix))
                ),
                java.util.Collections.<org.web3j.abi.TypeReference<?>>emptyList()
        );
        return broadcast(FunctionEncoder.encode(fn));
    }

    public String revoquerKycDirect(String walletAdresse, String motif) throws Exception {
        Function fn = new Function(
                "revoquerKyc",
                List.<Type>of(
                        new Address(walletAdresse),
                        new Utf8String(motif == null ? "" : motif)
                ),
                java.util.Collections.<org.web3j.abi.TypeReference<?>>emptyList()
        );
        return broadcast(FunctionEncoder.encode(fn));
    }

    // =========================================================================
    // Hashing : keccak256(prenom || nom || dateNaissance || numeroPiece || salt)
    // =========================================================================

    /**
     * Calcule le hash KYC RGPD-safe. Le sel secret est concatene a la fin :
     * sans le sel, un attaquant ne peut pas brute-forcer les hashes pour
     * deviner les identites des titulaires.
     *
     * Normalisation des inputs : lowercase + trim. Permet a un regulateur de
     * regenerer le hash sans depender de la casse de saisie.
     */
    public String hashKyc(String prenom, String nom, LocalDate dateNaissance, String numeroPiece) {
        String payload = String.join("|",
                norm(prenom),
                norm(nom),
                dateNaissance == null ? "" : dateNaissance.toString(),
                norm(numeroPiece),
                salt
        );
        byte[] digest = Hash.sha3(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "0x" + Numeric.toHexStringNoPrefix(digest);
    }

    /**
     * Verifie qu'un dossier off-chain (donne aux mains d'un regulateur) match
     * le hash inscrit on-chain pour un wallet donne. Le service ne fait que
     * recalculer le hash : la verification on-chain est faite via le contrat
     * (methode verifierHash) ou par comparaison directe avec l'event emis.
     */
    public String hashAttendu(String prenom, String nom, LocalDate dateNaissance, String numeroPiece) {
        return hashKyc(prenom, nom, dateNaissance, numeroPiece);
    }

    // =========================================================================
    // Helpers payload (pour la queue)
    // =========================================================================

    public static Map<String, Object> payloadEnregistrerKyc(
            String walletAdresse, String hashHex, long dateValidationUnix, long expireLeUnix) {
        Map<String, Object> m = new HashMap<>();
        m.put("walletAdresse", walletAdresse);
        m.put("hashHex", hashHex);
        m.put("dateValidationUnix", dateValidationUnix);
        m.put("expireLeUnix", expireLeUnix);
        return m;
    }

    public static Map<String, Object> payloadRevoquerKyc(String walletAdresse, String motif) {
        Map<String, Object> m = new HashMap<>();
        m.put("walletAdresse", walletAdresse);
        m.put("motif", motif);
        return m;
    }

    // =========================================================================
    // Helpers techniques
    // =========================================================================

    private boolean estEligible(String walletAdresse) {
        if (!estActif()) {
            log.debug("[KycLedger] Skip : registry non configure");
            return false;
        }
        if (walletAdresse == null || walletAdresse.isBlank() || !walletAdresse.startsWith("0x")) {
            log.debug("[KycLedger] Skip : adresse wallet invalide ({})", walletAdresse);
            return false;
        }
        return true;
    }

    private String norm(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }

    private byte[] parseBytes32(String hex) {
        String clean = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        if (clean.length() != 64) {
            throw new IllegalArgumentException("Hash KYC longueur invalide : " + clean.length());
        }
        return Numeric.hexStringToByteArray(clean);
    }

    private String broadcast(String encodedData) throws Exception {
        String nonceHex = rpc.getNonce(credentials.getAddress());
        BigInteger nonce = Numeric.decodeQuantity(nonceHex);

        RawTransaction tx = RawTransaction.createTransaction(
                nonce,
                BigInteger.valueOf(gasPrice),
                BigInteger.valueOf(gasLimit),
                kycRegistryAddress,
                BigInteger.ZERO,
                encodedData
        );
        byte[] signed = TransactionEncoder.signMessage(tx, chainId, credentials);
        String hex = Numeric.toHexString(signed);

        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\","
                + "\"params\":[\"" + hex + "\"],\"id\":1}";
        var response = rpc.sendRpc(body);
        String responseBody = response.body();
        if (!responseBody.contains("\"result\":\"")) {
            throw new RuntimeException("Erreur broadcast KycRegistry : " + responseBody);
        }
        return responseBody.split("\"result\":\"")[1].split("\"")[0];
    }
}
