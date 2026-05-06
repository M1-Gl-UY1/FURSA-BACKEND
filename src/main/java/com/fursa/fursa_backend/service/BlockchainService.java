package com.fursa.fursa_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
public class BlockchainService {

    @Value("${blockchain.rpc-url}")
    private String rpcUrl;

    @Value("${blockchain.contrat-adresse}")
    private String contratAdresse;

    // ── Client HTTP ───────────────────────────────────────────────────────
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

        log.info("Status HTTP : {}", response.statusCode());
        log.info("Réponse : {}", response.body());

        return response;
    }

    // ── Lire le nombre de parts disponibles ──────────────────────────────
    public BigInteger getPartsDisponibles() {
        try {
            String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":"
                    + "[{\"to\":\"" + contratAdresse.trim() + "\","
                    + "\"data\":\"0xa36c8d18\"},\"latest\"],\"id\":1}";

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

            log.error("Pas de result dans la réponse : {}", responseBody);
            return BigInteger.ZERO;

        } catch (Exception e) {
            log.error("Erreur getPartsDisponibles : {}", e.getMessage(), e);
            return BigInteger.ZERO;
        }
    }

    // ── Vérifier la connexion ─────────────────────────────────────────────
    public boolean estConnecte() {
        try {
            String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\","
                    + "\"params\":[],\"id\":1}";

            HttpResponse<String> response = sendRpc(body);
            boolean connecte = response.body().contains("\"result\"");
            log.info("Connecté : {}", connecte);
            return connecte;

        } catch (Exception e) {
            log.error("Blockchain non accessible : {}", e.getMessage());
            return false;
        }
    }

    // ── Récupérer le nonce ────────────────────────────────────────────────
    public String getNonce(String address) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getTransactionCount\","
                + "\"params\":[\"" + address + "\",\"latest\"],\"id\":1}";
        HttpResponse<String> response = sendRpc(body);
        return response.body().split("\"result\":\"")[1].split("\"")[0];
    }

    // ── Récupérer le receipt d'une transaction ────────────────────────────
    // ── Récupérer le receipt d'une transaction ────────────────────────────
    public String getAdresseContratFromReceipt(String txHash) throws Exception {
        Thread.sleep(2000);

        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getTransactionReceipt\","
                + "\"params\":[\"" + txHash + "\"],\"id\":1}";
        HttpResponse<String> response = sendRpc(body);
        String responseBody = response.body();

        log.info("Receipt complet : {}", responseBody);

        if (responseBody.contains("\"contractAddress\":\"")) {
            return responseBody.split("\"contractAddress\":\"")[1].split("\"")[0];
        }

        if (responseBody.contains("\"contractAddress\":null")) {
            throw new RuntimeException("contractAddress est null - transaction non minée ou échec");
        }

        throw new RuntimeException("Adresse du contrat introuvable : " + responseBody);
    }
}