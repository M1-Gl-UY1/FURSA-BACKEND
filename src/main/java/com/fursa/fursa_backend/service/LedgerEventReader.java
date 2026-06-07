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
 * V2 Q (07/06/2026) : decode les events emis par le RevenueLedger pour
 * alimenter la page admin "Audit on-chain".
 *
 * Utilise eth_getLogs en JSON-RPC raw (pas de wrapper Web3j genere pour
 * eviter une dependance de plus). Le decodage des topics + data est fait
 * a la main suivant la spec ABI.
 *
 * Performance : pour Sepolia / Polygon, un range de 10 000 blocs (~33 minutes
 * sur Polygon ou 33h sur Sepolia) suffit pour la plupart des cas d'admin
 * (vue "derniers events"). Pour un historique complet, paginer via blockFrom/To.
 */
@Service
@Slf4j
public class LedgerEventReader {

    /** Topic[0] = keccak256("RevenuEnregistre(address,uint256,uint32,uint256,bytes32,uint64)"). */
    private static final String TOPIC_REVENU_ENREGISTRE = sigHash(
            "RevenuEnregistre(address,uint256,uint32,uint256,bytes32,uint64)");

    /** Topic[0] = keccak256("DividendeDistribue(address,address,uint256,uint256)"). */
    private static final String TOPIC_DIVIDENDE_DISTRIBUE = sigHash(
            "DividendeDistribue(address,address,uint256,uint256)");

    private final BlockchainRpcClient rpc;
    private final String ledgerAddress;

    public LedgerEventReader(
            BlockchainRpcClient rpc,
            @Value("${blockchain.revenue-ledger-address:}") String ledgerAddress) {
        this.rpc = rpc;
        this.ledgerAddress = ledgerAddress == null ? "" : ledgerAddress.trim();
    }

    public boolean estActif() {
        return !ledgerAddress.isEmpty();
    }

    /**
     * Recupere les events sur les N derniers blocs (defaut 10 000). Retour
     * trie par blockNumber decroissant (plus recent en premier).
     *
     * @param maxBlocks nombre de blocs a remonter depuis le bloc courant
     */
    public List<LedgerEventResponse> getRecent(int maxBlocks) {
        if (!estActif()) {
            log.debug("[LedgerReader] Ledger non configure, no-op");
            return Collections.emptyList();
        }
        try {
            BigInteger latest = getLatestBlock();
            BigInteger from = latest.subtract(BigInteger.valueOf(Math.max(1, maxBlocks)));
            if (from.signum() < 0) from = BigInteger.ZERO;
            return getRange(from, latest);
        } catch (Exception e) {
            log.error("[LedgerReader] Echec lecture events : {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Recupere les events sur un range explicite. blockTo peut etre null = latest.
     */
    public List<LedgerEventResponse> getRange(BigInteger blockFrom, BigInteger blockTo) {
        if (!estActif()) return Collections.emptyList();
        try {
            List<LedgerEventResponse> all = new ArrayList<>();
            all.addAll(fetchAndDecode(blockFrom, blockTo, TOPIC_REVENU_ENREGISTRE));
            all.addAll(fetchAndDecode(blockFrom, blockTo, TOPIC_DIVIDENDE_DISTRIBUE));
            // Tri decroissant par blockNumber pour afficher le plus recent en haut.
            all.sort((a, b) -> b.blockNumber().compareTo(a.blockNumber()));
            return all;
        } catch (Exception e) {
            log.error("[LedgerReader] Echec lecture range {}-{} : {}",
                    blockFrom, blockTo, e.getMessage(), e);
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
                + "\"address\":\"" + ledgerAddress + "\","
                + "\"fromBlock\":\"" + fromHex + "\","
                + "\"toBlock\":\"" + toHex + "\","
                + "\"topics\":[\"" + topic0 + "\"]"
                + "}],\"id\":1}";

        var response = rpc.sendRpc(body);
        String responseBody = response.body();
        if (!responseBody.contains("\"result\":[")) {
            log.warn("[LedgerReader] reponse RPC inattendue : {}", responseBody);
            return Collections.emptyList();
        }
        return parseLogsArray(responseBody, topic0);
    }

    /**
     * Parser tres simple du tableau de logs JSON. On evite jackson pour ne pas
     * elargir la surface ; les logs etheriens ont un schema stable.
     */
    private List<LedgerEventResponse> parseLogsArray(String json, String topic0) {
        List<LedgerEventResponse> out = new ArrayList<>();
        int start = json.indexOf("\"result\":[");
        if (start < 0) return out;
        // Position du premier '{' apres "result":[
        int idx = json.indexOf('{', start);
        while (idx >= 0) {
            int end = findMatchingBrace(json, idx);
            if (end < 0) break;
            String logJson = json.substring(idx, end + 1);
            try {
                LedgerEventResponse ev = decodeOneLog(logJson, topic0);
                if (ev != null) out.add(ev);
            } catch (Exception e) {
                log.warn("[LedgerReader] log indecodable : {}", e.getMessage());
            }
            idx = json.indexOf('{', end + 1);
            // Stop si on est sorti du tableau (premier ']' apres la position courante)
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

        if (topics.isEmpty() || !topic0.equalsIgnoreCase(topics.get(0))) {
            return null;
        }

        BigInteger blockNumber = blockNumberHex == null ? BigInteger.ZERO
                : Numeric.decodeQuantity(blockNumberHex);

        if (TOPIC_REVENU_ENREGISTRE.equalsIgnoreCase(topic0)) {
            return decodeRevenu(txHash, blockNumber, topics, dataHex);
        } else if (TOPIC_DIVIDENDE_DISTRIBUE.equalsIgnoreCase(topic0)) {
            return decodeDividende(txHash, blockNumber, topics, dataHex);
        }
        return null;
    }

    private LedgerEventResponse decodeRevenu(String txHash, BigInteger blockNumber,
                                              List<String> topics, String dataHex) {
        // topics : [topic0, propAddress, revenuIdBackend]
        // data   : trimestre (uint32 pad32) | montantUsd (uint256) | hashJustif (bytes32) | dateValidation (uint64 pad32)
        String prop = topicToAddress(topics.get(1));
        BigInteger revenuId = topicToUint(topics.get(2));

        byte[] data = Numeric.hexStringToByteArray(strip0x(dataHex));
        BigInteger trimestre  = readUint256(data, 0);
        BigInteger montant    = readUint256(data, 32);
        String     hashJustif = "0x" + Numeric.toHexStringNoPrefix(
                java.util.Arrays.copyOfRange(data, 64, 96));
        BigInteger dateUnix   = readUint256(data, 96);

        return new LedgerEventResponse(
                LedgerEventResponse.Type.REVENU_ENREGISTRE,
                txHash, blockNumber, prop,
                trimestre.intValueExact(), montant,
                hashJustif, dateUnix.longValueExact(),
                null, revenuId
        );
    }

    private LedgerEventResponse decodeDividende(String txHash, BigInteger blockNumber,
                                                 List<String> topics, String dataHex) {
        // topics : [topic0, propAddress, investisseur, revenuIdBackend]
        // data   : montantUsd (uint256)
        String prop = topicToAddress(topics.get(1));
        String inv  = topicToAddress(topics.get(2));
        BigInteger revenuId = topicToUint(topics.get(3));

        byte[] data = Numeric.hexStringToByteArray(strip0x(dataHex));
        BigInteger montant = readUint256(data, 0);

        return new LedgerEventResponse(
                LedgerEventResponse.Type.DIVIDENDE_DISTRIBUE,
                txHash, blockNumber, prop,
                null, montant,
                null, null,
                inv, revenuId
        );
    }

    // =========================================================================
    // Helpers JSON / ABI
    // =========================================================================

    private static String sigHash(String signature) {
        return "0x" + Numeric.toHexStringNoPrefix(Hash.sha3(signature.getBytes()));
    }

    private static String extractField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
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
        // Topic = 32 bytes, adresse = 20 derniers bytes (pad gauche).
        String clean = strip0x(topic);
        if (clean.length() < 40) return "0x" + clean;
        return "0x" + clean.substring(clean.length() - 40);
    }

    private static BigInteger topicToUint(String topic) {
        return Numeric.decodeQuantity(topic);
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
