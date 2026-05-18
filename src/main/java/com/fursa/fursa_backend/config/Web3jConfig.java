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
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));
        try {
            var block = web3j.ethBlockNumber().send();
            System.out.println("Blockchain RPC OK (" + rpcUrl + ") - bloc: " + block.getBlockNumber());
        } catch (Exception e) {
            System.err.println("Blockchain RPC injoignable (" + rpcUrl + "): " + e.getMessage());
        }
        return web3j;
    }

    @Bean
    public Credentials credentials() {
        if (ownerPrivateKey == null || ownerPrivateKey.isBlank()) {
            throw new IllegalStateException(
                "BLOCKCHAIN_OWNER_PRIVATE_KEY non configuree. " +
                "Definir la variable d'environnement ou la propriete blockchain.owner-private-key.");
        }
        return Credentials.create(ownerPrivateKey);
    }

    @Bean
    public TransactionManager web3TransactionManager(
            Web3j web3j,
            Credentials credentials,
            @Value("${blockchain.chain-id}") long chainId) {
        return new RawTransactionManager(
            web3j,
            credentials,
            chainId,
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