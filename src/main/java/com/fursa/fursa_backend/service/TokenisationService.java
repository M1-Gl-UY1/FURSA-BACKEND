package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class TokenisationService {

    /** V2 O (07/06/2026) : version par defaut pour tout nouveau deploiement. */
    public static final String CONTRAT_VERSION_DEFAUT = "V2";

    private final ProprieteRepository proprieteRepository;
    private final BlockchainRpcClient blockchainRpcClient;
    private final Credentials credentials;
    private final long chainId;
    private final long gasPrice;
    private final long gasLimit;

    public TokenisationService(
            ProprieteRepository proprieteRepository,
            BlockchainRpcClient blockchainRpcClient,
            Credentials credentials,
            @Value("${blockchain.chain-id}") long chainId,
            // Gas params injectes depuis .env (avant : hardcode 1 Gwei + 3M gas).
            // Defaut 20 Gwei pour fiabilite sur Sepolia (block time ~12s, mempool souvent congestionne).
            @Value("${blockchain.gas-price:20000000000}") long gasPrice,
            @Value("${blockchain.gas-limit:3000000}") long gasLimit) {
        this.proprieteRepository = proprieteRepository;
        this.blockchainRpcClient = blockchainRpcClient;
        this.credentials = credentials;
        this.chainId = chainId;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        log.info("TokenisationService initialise : chainId={} gasPrice={} Gwei gasLimit={}",
                chainId, gasPrice / 1_000_000_000L, gasLimit);
    }

    /**
     * Lance la tokenisation en async : broadcast la transaction Ethereum, stocke
     * txHash + statut EN_TOKENISATION puis renvoie immediatement. Un worker
     * scheduled ({@link TokenisationWorker}) poll ensuite le receipt et bascule
     * la propriete en PUBLIEE quand l'adresse du contrat est disponible.
     *
     * <p>Resout le bug "Adresse du contrat introuvable : result:null" qui survenait
     * quand le receipt etait demande avant que la tx soit minee sur Sepolia
     * (block time ~12s, parfois plusieurs minutes en cas de pic gas).
     */
    @Transactional
    public Propriete lancerTokenisation(Long id) throws Exception {
        log.info("=== LANCEMENT TOKENISATION ASYNC propriete {} ===", id);

        Propriete propriete = proprieteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriete introuvable : " + id));

        if (propriete.getStatut() != StatutPropriete.ACCEPTEE) {
            throw new RuntimeException(
                "La propriete doit etre ACCEPTEE pour lancer la tokenisation. Statut actuel : "
                    + propriete.getStatut());
        }
        if (propriete.getTransactionHash() != null && !propriete.getTransactionHash().isBlank()) {
            throw new RuntimeException(
                "Ce bien est deja tokenise (tx hash : " + propriete.getTransactionHash() + ")");
        }

        String txHash = broadcastDeploiement(propriete);
        log.info("Tx broadcast OK, hash={}", txHash);

        propriete.setTransactionHash(txHash);
        propriete.setStatut(StatutPropriete.EN_TOKENISATION);
        // V2 O (07/06/2026) : nouveaux deploiements basculent en V2 (prix mutable).
        propriete.setContratVersion(CONTRAT_VERSION_DEFAUT);
        Propriete saved = proprieteRepository.save(propriete);
        log.info("Propriete {} passe en EN_TOKENISATION (contrat={}), worker prendra le relais",
                id, CONTRAT_VERSION_DEFAUT);
        return saved;
    }

    /**
     * Construit, signe et broadcast la transaction de deploiement du smart contract
     * pour cette propriete. Renvoie le txHash. Ne touche pas a la BDD.
     *
     * V2 O (07/06/2026) : utilise ProprieteTokenV2 (prix mutable + devise + statut).
     * Devise FURSA = USD (decision Hugh 22/05).
     */
    private String broadcastDeploiement(Propriete propriete) throws Exception {
        String bytecode = lireBytecodeV2();
        String encodedParams = encodeConstructorParamsV2(
                propriete.getNom(),
                propriete.getId(),
                propriete.getNombreTotalPart(),
                propriete.getPrixUnitairePart().toBigInteger(),
                "USD"
        );
        String data = bytecode + encodedParams;

        String nonceHex = blockchainRpcClient.getNonce(credentials.getAddress());
        BigInteger nonce = Numeric.decodeQuantity(nonceHex);

        RawTransaction rawTx = RawTransaction.createContractTransaction(
                nonce,
                BigInteger.valueOf(gasPrice),
                BigInteger.valueOf(gasLimit),
                BigInteger.ZERO,
                data
        );
        byte[] signedTx = TransactionEncoder.signMessage(rawTx, chainId, credentials);
        String hexTx = Numeric.toHexString(signedTx);

        String sendBody = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\","
                + "\"params\":[\"" + hexTx + "\"],\"id\":1}";

        var response = blockchainRpcClient.sendRpc(sendBody);
        String responseBody = response.body();

        if (!responseBody.contains("\"result\":\"")) {
            throw new RuntimeException("Erreur broadcast tx : " + responseBody);
        }
        return responseBody.split("\"result\":\"")[1].split("\"")[0];
    }

    /**
     * Methode historique : broadcast + attente synchrone du receipt + maj BDD.
     * Garde pour compat retour mais ne devrait plus etre appelee par le workflow
     * de validation admin (cf lancerTokenisation + worker).
     */
    @Transactional
    public Propriete tokeniserPropriete(Long id) throws Exception {

        log.info("=== DEBUT TOKENISATION propriété {} ===", id);

        // 1. Vérifie que la propriété existe
        Propriete propriete = proprieteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété introuvable : " + id));
        log.info("Propriété trouvée : {}", propriete.getNom());

        // 2. Vérifie le statut (Hugh 22/05/2026 00:15:54 : tokeniser uniquement APRES
        // la validation admin pour eviter d'avoir des biens non verifies on-chain).
        // Statuts acceptes : ACCEPTEE (valide pret a publier) ou PUBLIEE (deja en ligne).
        // Le legacy EN_ATTENTE reste accepte pour ne pas casser les anciens biens.
        if (propriete.getStatut() != StatutPropriete.ACCEPTEE
                && propriete.getStatut() != StatutPropriete.PUBLIEE
                && propriete.getStatut() != StatutPropriete.EN_ATTENTE) {
            throw new RuntimeException(
                "La propriété doit etre ACCEPTEE ou PUBLIEE pour etre tokenisee. Statut actuel : "
                    + propriete.getStatut()
            );
        }
        // Idempotence : si deja tokenisee, on refuse.
        if (propriete.getTransactionHash() != null && !propriete.getTransactionHash().isBlank()) {
            throw new RuntimeException(
                "Ce bien est deja tokenise (tx hash : " + propriete.getTransactionHash() + ")"
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
        String nonceHex = blockchainRpcClient.getNonce(credentials.getAddress());
        log.info("NonceHex brut : {}", nonceHex);
        BigInteger nonce = Numeric.decodeQuantity(nonceHex);
        log.info("Nonce : {}", nonce);

        // 7. Crée et signe la transaction
        log.info("Signature transaction...");

        RawTransaction rawTx = RawTransaction.createContractTransaction(
                nonce,
                BigInteger.valueOf(gasPrice),
                BigInteger.valueOf(gasLimit),
                BigInteger.ZERO,
                data
        );

        byte[] signedTx = TransactionEncoder.signMessage(rawTx, chainId, credentials);
        String hexTx = Numeric.toHexString(signedTx);
        log.info("Transaction signée, longueur : {}", hexTx.length());

        // 8. Envoie la transaction
        log.info("Envoi transaction...");
        String sendBody = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_sendRawTransaction\","
                + "\"params\":[\"" + hexTx + "\"],\"id\":1}";

        var response = blockchainRpcClient.sendRpc(sendBody);
        String responseBody = response.body();
        log.info("Réponse déploiement complète : {}", responseBody);

        if (!responseBody.contains("\"result\":\"")) {
            throw new RuntimeException("Erreur déploiement : " + responseBody);
        }

        String txHash = responseBody.split("\"result\":\"")[1].split("\"")[0];
        log.info("Transaction hash : {}", txHash);

        // 9. Récupère l'adresse du contrat déployé
        log.info("Récupération adresse contrat...");
        String adresseContrat = blockchainRpcClient.getAdresseContratFromReceipt(txHash);
        log.info("Contrat déployé à : {}", adresseContrat);

        // 10. Met à jour la propriété en base
        propriete.setAdresseContrat(adresseContrat);
        propriete.setTransactionHash(txHash);
        propriete.setStatut(StatutPropriete.PUBLIEE);

        Propriete saved = proprieteRepository.save(propriete);
        log.info("=== TOKENISATION TERMINÉE === adresse : {}", adresseContrat);

        return saved;
    }

    // ── V2 O : lit le bytecode du contrat V2 ──────────────────────────────
    private String lireBytecodeV2() throws Exception {
        return lireBytecodeDepuis("/abi/ProprieteTokenV2.json");
    }

    // ── V2 O : encode le constructeur V2 (5 params : nom, id, parts, prix, devise) ──
    // ABI layout pour deux strings + trois uints :
    //   offset string1 (32) | id (32) | parts (32) | prix (32) | offset string2 (32)
    //   | length string1 (32) | content string1 (padded) | length string2 (32) | content string2 (padded)
    private String encodeConstructorParamsV2(
            String nom, Long idBackend, Integer nombreParts,
            BigInteger prixInitial, String devise) {
        byte[] nomBytes    = nom.getBytes(StandardCharsets.UTF_8);
        byte[] deviseBytes = devise.getBytes(StandardCharsets.UTF_8);

        // Position du 1er string : 5 mots de 32 bytes = 160 octets
        int offsetNom = 32 * 5;
        // Position du 2eme string : offsetNom + length nom (32) + contenu nom paddé
        int offsetDevise = offsetNom + 32 + alignTo32(nomBytes.length);

        String offNomHex     = pad32(BigInteger.valueOf(offsetNom));
        String idEncoded     = pad32(BigInteger.valueOf(idBackend));
        String partsEncoded  = pad32(BigInteger.valueOf(nombreParts));
        String prixEncoded   = pad32(prixInitial);
        String offDeviseHex  = pad32(BigInteger.valueOf(offsetDevise));
        String nomLen        = pad32(BigInteger.valueOf(nomBytes.length));
        String nomContent    = padRight(Numeric.toHexStringNoPrefix(nomBytes));
        String deviseLen     = pad32(BigInteger.valueOf(deviseBytes.length));
        String deviseContent = padRight(Numeric.toHexStringNoPrefix(deviseBytes));

        return offNomHex + idEncoded + partsEncoded + prixEncoded + offDeviseHex
                + nomLen + nomContent + deviseLen + deviseContent;
    }

    private static int alignTo32(int n) {
        int rem = n % 32;
        return rem == 0 ? n : n + (32 - rem);
    }

    private String lireBytecodeDepuis(String resourcePath) throws Exception {
        var is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new RuntimeException("Fichier introuvable : " + resourcePath);
        }
        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        int idx = json.indexOf("\"bytecode\": \"0x");
        if (idx == -1) idx = json.indexOf("\"bytecode\":\"0x");
        if (idx == -1) throw new RuntimeException("bytecode introuvable dans " + resourcePath);
        int start = json.indexOf("0x", idx);
        int end = json.indexOf("\"", start);
        return json.substring(start + 2, end);  // sans le 0x
    }

    // ── Lit le bytecode V1 historique ─────────────────────────────────────
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