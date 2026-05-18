package com.fursa.fursa_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;

/**
 * Client RPC JSON bas-niveau pour Ethereum (HttpClient JDK).
 * Utilise par TokenisationService pour le deploiement direct de smart contracts.
 *
 * NOTE : Coexiste avec com.fursa.fursa_backend.blockchain.service.BlockchainService
 * qui utilise web3j pour les autres operations. A unifier sur web3j a terme.
 */
@Slf4j
@Service
public class BlockchainRpcClient {

    @Value("${blockchain.rpc-url}")
    private String rpcUrl;

    @Value("${blockchain.contract-address}")
    private String contratAdresse;

    private HttpClient buildClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public HttpResponse<String> sendRpc(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(rpcUrl.trim()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = buildClient().send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        log.debug("RPC status={} body={}", response.statusCode(), response.body());
        return response;
    }

    public BigInteger getPartsDisponibles() {
        try {
            // Encode dynamiquement le selector de la fonction partsDisponibles() du ProprieteToken.
            // Plus robuste qu'un hardcode 0xa36c8d18 qui devient faux si le contrat change.
            Function fn = new Function(
                    "partsDisponibles",
                    Collections.emptyList(),
                    Collections.emptyList());
            String data = FunctionEncoder.encode(fn);

            String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":"
                    + "[{\"to\":\"" + contratAdresse.trim() + "\","
                    + "\"data\":\"" + data + "\"},\"latest\"],\"id\":1}";

            HttpResponse<String> response = sendRpc(body);
            String responseBody = response.body();

            if (responseBody.contains("\"result\":\"")) {
                String result = responseBody
                        .split("\"result\":\"")[1]
                        .split("\"")[0];
                BigInteger valeur = new BigInteger(result.replace("0x", ""), 16);
                log.info("Parts disponibles : {}", valeur);
                return valeur;
            }

            log.error("Pas de result dans la reponse : {}", responseBody);
            return BigInteger.ZERO;

        } catch (Exception e) {
            log.error("Erreur getPartsDisponibles : {}", e.getMessage());
            return BigInteger.ZERO;
        }
    }

    public boolean estConnecte() {
        try {
            String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\","
                    + "\"params\":[],\"id\":1}";

            HttpResponse<String> response = sendRpc(body);
            boolean connecte = response.body().contains("\"result\"");
            log.info("Connecte : {}", connecte);
            return connecte;

        } catch (Exception e) {
            log.error("Blockchain non accessible : {}", e.getMessage());
            return false;
        }
    }

    public String getNonce(String address) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getTransactionCount\","
                + "\"params\":[\"" + address + "\",\"latest\"],\"id\":1}";
        HttpResponse<String> response = sendRpc(body);
        return response.body().split("\"result\":\"")[1].split("\"")[0];
    }

    public String getAdresseContratFromReceipt(String txHash) throws Exception {
        Thread.sleep(2000);

        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getTransactionReceipt\","
                + "\"params\":[\"" + txHash + "\"],\"id\":1}";
        HttpResponse<String> response = sendRpc(body);
        String responseBody = response.body();

        log.info("Receipt : {}", responseBody);

        if (responseBody.contains("\"contractAddress\":\"")) {
            return responseBody.split("\"contractAddress\":\"")[1].split("\"")[0];
        }

        if (responseBody.contains("\"contractAddress\":null")) {
            throw new RuntimeException("contractAddress est null - transaction non minee ou echec");
        }

        throw new RuntimeException("Adresse du contrat introuvable : " + responseBody);
    }
}
