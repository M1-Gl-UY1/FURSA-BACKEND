package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.LedgerEventResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V2 T (07/06/2026) : reader des events emis par le contrat KycRegistry.
 *
 * Decode KycEnregistre + KycRevoque en LedgerEventResponse (unifie avec les
 * events RevenueLedger pour la page admin Audit on-chain).
 *
 * Si BLOCKCHAIN_KYC_REGISTRY_ADDRESS est vide, retourne une liste vide.
 */
@Service
@Slf4j
public class KycEventReader {

    private static final String TOPIC_KYC_ENREGISTRE = sigHash(
            "KycEnregistre(address,bytes32,uint64,uint64)");

    private static final String TOPIC_KYC_REVOQUE = sigHash(
            "KycRevoque(address,uint64,string)");

    private final BlockchainRpcClient rpc;
    private final String kycRegistryAddress;

    public KycEventReader(
            BlockchainRpcClient rpc,
            @Value("${blockchain.kyc-registry-address:}") String kycRegistryAddress) {
        this.rpc = rpc;
        this.kycRegistryAddress = kycRegistryAddress == null ? "" : kycRegistryAddress.trim();
    }

    public boolean estActif() {
        return !kycRegistryAddress.isEmpty();
    }

    public List<LedgerEventResponse> getRecent(int maxBlocks) {
        if (!estActif()) return Collections.emptyList();
        try {
            BigInteger latest = getLatestBlock();
            BigInteger from = latest.subtract(BigInteger.valueOf(Math.max(1, maxBlocks)));
            if (from.signum() < 0) from = BigInteger.ZERO;
            List<LedgerEventResponse> all = new ArrayList<>();
            all.addAll(fetchAndDecode(from, latest, TOPIC_KYC_ENREGISTRE));
            all.addAll(fetchAndDecode(from, latest, TOPIC_KYC_REVOQUE));
            all.sort((a, b) -> b.blockNumber().compareTo(a.blockNumber()));
            return all;
        } catch (Exception e) {
            log.error("[KycReader] Echec lecture events : {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private BigInteger getLatestBlock() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\","
                + "\"params\":[],\"id\":1}";
        var response = rpc.sendRpc(body);
        String result = response.body().split("\"result\":\"")[1].split("\"")[0];
        return Numeric.decodeQuantity(result);
    }

    private List<LedgerEventResponse> fetchAndDecode(BigInteger from, BigInteger to,
                                                     String topic0) throws Exception {
        String fromHex = "0x" + from.toString(16);
        String toHex = to == null ? "latest" : "0x" + to.toString(16);

        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{"
                + "\"address\":\"" + kycRegistryAddress + "\","
                + "\"fromBlock\":\"" + fromHex + "\","
                + "\"toBlock\":\"" + toHex + "\","
                + "\"topics\":[\"" + topic0 + "\"]"
                + "}],\"id\":1}";

        var response = rpc.sendRpc(body);
        String responseBody = response.body();
        if (!responseBody.contains("\"result\":[")) {
            log.warn("[KycReader] reponse RPC inattendue : {}", responseBody);
            return Collections.emptyList();
        }
        return parseLogsArray(responseBody, topic0);
    }

    private List<LedgerEventResponse> parseLogsArray(String json, String topic0) {
        List<LedgerEventResponse> out = new ArrayList<>();
        int start = json.indexOf("\"result\":[");
        if (start < 0) return out;
        int idx = json.indexOf('{', start);
        while (idx >= 0) {
            int end = findMatchingBrace(json, idx);
            if (end < 0) break;
            String logJson = json.substring(idx, end + 1);
            try {
                LedgerEventResponse ev = decodeOneLog(logJson, topic0);
                if (ev != null) out.add(ev);
            } catch (Exception e) {
                log.warn("[KycReader] log indecodable : {}", e.getMessage());
            }
            idx = json.indexOf('{', end + 1);
            int closeArr = json.indexOf(']', end + 1);
            if (closeArr >= 0 && idx > closeArr) break;
        }
        return out;
    }

    private int findMatchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private LedgerEventResponse decodeOneLog(String logJson, String topic0) {
        String txHash = extractField(logJson, "transactionHash");
        String blockNumberHex = extractField(logJson, "blockNumber");
        String dataHex = extractField(logJson, "data");
        List<String> topics = extractTopics(logJson);

        if (topics.isEmpty() || !topic0.equalsIgnoreCase(topics.get(0))) return null;

        BigInteger blockNumber = blockNumberHex == null ? BigInteger.ZERO
                : Numeric.decodeQuantity(blockNumberHex);

        if (TOPIC_KYC_ENREGISTRE.equalsIgnoreCase(topic0)) {
            return decodeEnregistre(txHash, blockNumber, topics, dataHex);
        } else if (TOPIC_KYC_REVOQUE.equalsIgnoreCase(topic0)) {
            return decodeRevoque(txHash, blockNumber, topics, dataHex);
        }
        return null;
    }

    private LedgerEventResponse decodeEnregistre(String txHash, BigInteger blockNumber,
                                                  List<String> topics, String dataHex) {
        // topics : [topic0, wallet]
        // data : hashKyc (bytes32) | dateValidation (uint64 pad32) | expireLe (uint64 pad32)
        String wallet = topicToAddress(topics.get(1));
        byte[] data = Numeric.hexStringToByteArray(strip0x(dataHex));
        String hashKyc = "0x" + Numeric.toHexStringNoPrefix(
                java.util.Arrays.copyOfRange(data, 0, 32));
        BigInteger dateValid = readUint256(data, 32);
        BigInteger expireLe = readUint256(data, 64);

        return new LedgerEventResponse(
                LedgerEventResponse.Type.KYC_ENREGISTRE,
                txHash, blockNumber, null,
                null, null,
                hashKyc, dateValid.longValueExact(),
                wallet, null,
                expireLe.longValueExact(), null
        );
    }

    private LedgerEventResponse decodeRevoque(String txHash, BigInteger blockNumber,
                                               List<String> topics, String dataHex) {
        // topics : [topic0, wallet]
        // data : dateRevocation (uint64 pad32) | offset string | length string | content
        String wallet = topicToAddress(topics.get(1));
        byte[] data = Numeric.hexStringToByteArray(strip0x(dataHex));
        BigInteger dateRevoc = readUint256(data, 0);
        String motif = decodeStringAtOffset(data, 32);

        return new LedgerEventResponse(
                LedgerEventResponse.Type.KYC_REVOQUE,
                txHash, blockNumber, null,
                null, null,
                null, dateRevoc.longValueExact(),
                wallet, null,
                null, motif
        );
    }

    private String decodeStringAtOffset(byte[] data, int offsetWord) {
        // data[offsetWord..offsetWord+32] = offset (en octets) du debut du string
        BigInteger offBig = readUint256(data, offsetWord);
        int strOffset = offBig.intValueExact();
        if (strOffset + 32 > data.length) return "";
        BigInteger lenBig = readUint256(data, strOffset);
        int len = lenBig.intValueExact();
        int start = strOffset + 32;
        if (start + len > data.length) return "";
        return new String(data, start, len, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String sigHash(String signature) {
        return "0x" + Numeric.toHexStringNoPrefix(Hash.sha3(signature.getBytes()));
    }

    private static String extractField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) return null;
        int s = i + key.length();
        int e = json.indexOf('"', s);
        return e < 0 ? null : json.substring(s, e);
    }

    private static List<String> extractTopics(String json) {
        List<String> out = new ArrayList<>();
        int i = json.indexOf("\"topics\":[");
        if (i < 0) return out;
        int j = json.indexOf(']', i);
        if (j < 0) return out;
        String arr = json.substring(i + 10, j);
        for (String s : arr.split(",")) {
            String clean = s.trim().replace("\"", "");
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
    }

    private static String topicToAddress(String topic) {
        String clean = strip0x(topic);
        if (clean.length() < 40) return "0x" + clean;
        return "0x" + clean.substring(clean.length() - 40);
    }

    private static BigInteger readUint256(byte[] data, int offset) {
        if (offset + 32 > data.length) return BigInteger.ZERO;
        byte[] slice = new byte[32];
        System.arraycopy(data, offset, slice, 0, 32);
        return new BigInteger(1, slice);
    }

    private static String strip0x(String s) {
        if (s == null) return "";
        return (s.startsWith("0x") || s.startsWith("0X")) ? s.substring(2) : s;
    }
}
