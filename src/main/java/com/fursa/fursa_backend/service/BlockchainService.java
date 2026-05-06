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

    
    // ── Client HTTP partagé ───────────────────────────────────────────────
    private HttpClient buildClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
    private HttpResponse<String> sendRpc(String body) throws Exception {
        log.info("RPC URL : '{}'", rpcUrl.trim());
        log.info("Body envoyé : {}", body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(rpcUrl.trim()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        log.info("Headers : {}", request.headers().map());

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
}