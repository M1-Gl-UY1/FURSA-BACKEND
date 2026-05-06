package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenisationService {

    private final ProprieteRepository proprieteRepository;
    private final BlockchainService blockchainService;

    private static final String ADMIN_ADDRESS =
            "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    private static final String ADMIN_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final long CHAIN_ID = 31337L;

    @Transactional
    public Propriete tokeniserPropriete(Long id) throws Exception {

        log.info("=== DEBUT TOKENISATION propriété {} ===", id);

        // 1. Vérifie que la propriété existe
        Propriete propriete = proprieteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété introuvable : " + id));
        log.info("Propriété trouvée : {}", propriete.getNom());

        // 2. Vérifie le statut
        if (propriete.getStatut() != StatutPropriete.EN_ATTENTE) {
            throw new RuntimeException(
                "La propriété doit être EN_ATTENTE. Statut actuel : " + propriete.getStatut()
            );
        }
        log.info("Statut OK : {}", propriete.getStatut());

        // 3. Lit le bytecode
        log.info("Lecture bytecode...");
        String bytecode = lireBytecode();
        log.info("Bytecode OK, longueur : {}", bytecode.length());

        // 4. Encode les paramètres
        log.info("Encodage paramètres...");
        String encodedParams = encodeConstructorParams(
                propriete.getNom(),
                propriete.getId(),
                propriete.getNombreTotalPart(),
                propriete.getPrixUnitairePart().toBigInteger()
        );
        log.info("Params encodés, longueur : {}", encodedParams.length());

        // 5. Data = bytecode + params
        String data = bytecode + encodedParams;
        log.info("Data totale longueur : {}", data.length());

        // 6. Nonce
        log.info("Récupération nonce...");
        String nonceHex = blockchainService.getNonce(ADMIN_ADDRESS);
        log.info("NonceHex brut : {}", nonceHex);
        BigInteger nonce = Numeric.decodeQuantity(nonceHex);
        log.info("Nonce : {}", nonce);

        // 7. Crée et signe la transaction
        log.info("Signature transaction...");
        Credentials credentials = Credentials.create(ADMIN_PRIVATE_KEY);

        RawTransaction rawTx = RawTransaction.createContractTransaction(
                nonce,
                BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(3_000_000L),
                BigInteger.ZERO,
                data
        );

        byte[] signedTx = TransactionEncoder.signMessage(rawTx, CHAIN_ID, credentials);
        String hexTx = Numeric.toHexString(signedTx);
        log.info("Transaction signée, longueur : {}", hexTx.length());

        // 8. Envoie la transaction
        log.info("Envoi transaction...");
        String sendBody = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\","
                + "\"params\":[\"" + hexTx + "\"],\"id\":1}";

        var response = blockchainService.sendRpc(sendBody);
        String responseBody = response.body();
        log.info("Réponse déploiement complète : {}", responseBody);

        if (!responseBody.contains("\"result\":\"")) {
            throw new RuntimeException("Erreur déploiement : " + responseBody);
        }

        String txHash = responseBody.split("\"result\":\"")[1].split("\"")[0];
        log.info("Transaction hash : {}", txHash);

        // 9. Récupère l'adresse du contrat déployé
        log.info("Récupération adresse contrat...");
        String adresseContrat = blockchainService.getAdresseContratFromReceipt(txHash);
        log.info("Contrat déployé à : {}", adresseContrat);

        // 10. Met à jour la propriété en base
        propriete.setAdresseContrat(adresseContrat);
        propriete.setTransactionHash(txHash);
        propriete.setStatut(StatutPropriete.PUBLIEE);

        Propriete saved = proprieteRepository.save(propriete);
        log.info("=== TOKENISATION TERMINÉE === adresse : {}", adresseContrat);

        return saved;
    }

    // ── Lit le bytecode depuis le JSON compilé par Hardhat ────────────────
    private String lireBytecode() throws Exception {
        try {
            log.info("Ouverture fichier JSON...");
            var is = getClass().getResourceAsStream("/abi/ProprieteToken.json");
            if (is == null) {
                throw new RuntimeException("Fichier ProprieteToken.json introuvable");
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("JSON lu, longueur : {}", json.length());
    
            // Le JSON a un espace : "bytecode": "0x..."
            int idx = json.indexOf("\"bytecode\": \"0x");
            log.info("Index bytecode (avec espace) : {}", idx);
    
            if (idx == -1) {
                // Essaie sans espace
                idx = json.indexOf("\"bytecode\":\"0x");
                log.info("Index bytecode (sans espace) : {}", idx);
            }
    
            if (idx == -1) {
                throw new RuntimeException("bytecode introuvable dans le JSON");
            }
    
            // Trouve le début du 0x
            int start = json.indexOf("0x", idx);
            int end = json.indexOf("\"", start);
            log.info("Start : {}, End : {}", start, end);
    
            String bytecode = json.substring(start, end);
            log.info("Bytecode extrait, longueur : {}", bytecode.length());
    
            return bytecode.substring(2); // retire le 0x
    
        } catch (Exception e) {
            log.error("ERREUR dans lireBytecode : {}", e.getMessage(), e);
            throw e;
        }
    }

    // ── Encode les paramètres du constructeur en ABI ──────────────────────
    private String encodeConstructorParams(String nom, Long idBackend,
                                            Integer nombreParts, BigInteger prixParPart) {
        byte[] nomBytes = nom.getBytes(StandardCharsets.UTF_8);

        String offsetStr = pad32(BigInteger.valueOf(128));
        String idEncoded = pad32(BigInteger.valueOf(idBackend));
        String partsEncoded = pad32(BigInteger.valueOf(nombreParts));
        String prixEncoded = pad32(prixParPart);
        String nomLength = pad32(BigInteger.valueOf(nomBytes.length));
        String nomContent = padRight(Numeric.toHexStringNoPrefix(nomBytes));

        return offsetStr + idEncoded + partsEncoded + prixEncoded + nomLength + nomContent;
    }

    private String pad32(BigInteger value) {
        String hex = value.toString(16);
        return String.format("%064x", new java.math.BigInteger(hex, 16));
    }

    private String padRight(String hex) {
        int length = hex.length();
        int padding = 64 - (length % 64);
        if (padding == 64) return hex;
        return hex + "0".repeat(padding);
    }
}