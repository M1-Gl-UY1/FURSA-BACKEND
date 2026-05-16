package com.fursa.fursa_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.crypto.Credentials;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.tx.response.NoOpProcessor;
import java.math.BigInteger;

@Configuration
public class Web3jConfig {

    @Value("${blockchain.rpc-url}")
    private String rpcUrl;

    @Value("${blockchain.owner-private-key}")
    private String ownerPrivateKey;

    @Bean
    public Web3j web3j() {
        // ✅ Web3j.build — pas de lien Markdown
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));
        try {
            var block = web3j.ethBlockNumber().send();
            System.out.println("✅ Ganache connecté - bloc: "
                + block.getBlockNumber());
        } catch (Exception e) {
            System.err.println("❌ Ganache non disponible: " + e.getMessage());
        }
        return web3j;
    }

    // @Bean
    // public Credentials credentials() {
    //     Credentials creds = Credentials.create(ownerPrivateKey);
    //     System.out.println("👤 Adresse owner dérivée: " + creds.getAddress());
    //     return creds;
    // }

    @Bean
    public Credentials credentials() {
    // Test hardcodé — sans 0x
    String testKey = "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce9c46f30d7d21715b23b1d";
    Credentials creds = Credentials.create(testKey);
    System.out.println("👤 Test hardcodé: " + creds.getAddress());
    
    // Comparaison avec la valeur YAML
    Credentials credsYaml = Credentials.create(ownerPrivateKey);
    System.out.println("👤 Depuis YAML: " + credsYaml.getAddress());
    System.out.println("🔑 Clé YAML reçue: '" + ownerPrivateKey + "'");
    System.out.println("🔑 Longueur clé YAML: " + ownerPrivateKey.length());
    
    return Credentials.create(testKey);
}  


    @Bean
    public TransactionManager web3TransactionManager(Web3j web3j, Credentials credentials) {
        return new RawTransactionManager(
            web3j,
            credentials,
            1337L,
            new NoOpProcessor(web3j)
        );
    }

    @Bean
    public ContractGasProvider gasProvider(
            @Value("${blockchain.gas-price}") Long gasPrice,
            @Value("${blockchain.gas-limit}") Long gasLimit) {
        return new StaticGasProvider(
            BigInteger.valueOf(gasPrice),
            BigInteger.valueOf(gasLimit)
        );
    }
    
}