package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.blockchain.service.BlockchainService;
import com.fursa.fursa_backend.dto.BlockchainResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/blockchain")
@RequiredArgsConstructor
@Tag(name = "Blockchain", description = "Smoke tests on-chain (admin uniquement)")
public class BlockchainHealthController {

    private final BlockchainService blockchainService;
    private final Web3j web3j;
    private final Credentials credentials;

    @Value("${blockchain.contract-address}")
    private String contractAddress;

    @Value("${blockchain.chain-id}")
    private long chainId;

    @Operation(summary = "Etat de la connexion blockchain + solde du contrat et du wallet owner")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        // Tout le code est en try-catch pour TOUJOURS renvoyer un JSON exploitable
        // (et pas un 500 avec message generique masquant le diagnostic).
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            body.put("chainId", chainId);
            body.put("contractAddress", contractAddress);
            body.put("ownerAddress", credentials != null ? credentials.getAddress() : "credentials_NULL");
        } catch (Throwable t) {
            body.put("error_config", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        try {
            BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            body.put("rpcReachable", true);
            body.put("blockNumber", blockNumber);
        } catch (Throwable t) {
            body.put("rpcReachable", false);
            body.put("error_rpc", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        try {
            BigInteger ownerBalanceWei = web3j.ethGetBalance(
                    credentials.getAddress(),
                    DefaultBlockParameterName.LATEST
            ).send().getBalance();
            body.put("ownerBalanceWei", ownerBalanceWei);
            body.put("ownerBalanceEth", weiToEthString(ownerBalanceWei));
        } catch (Throwable t) {
            body.put("error_owner_balance", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        try {
            BigInteger contractBalanceWei = blockchainService.getContractBalance();
            body.put("contractBalanceWei", contractBalanceWei);
            body.put("contractBalanceEth", weiToEthString(contractBalanceWei));
        } catch (Throwable t) {
            body.put("error_contract_balance", t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Dividende stocke on-chain pour une adresse (wei)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/dividende/{address}")
    public ResponseEntity<BlockchainResponse> dividende(@PathVariable String address) {
        try {
            BigInteger dividend = blockchainService.getDividende(address);
            return ResponseEntity.ok(new BlockchainResponse(
                    true, null, "OK",
                    Map.of("address", address, "dividendWei", dividend.toString())
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(new BlockchainResponse(false, null, e.getMessage(), null));
        }
    }

    private static String weiToEthString(BigInteger wei) {
        java.math.BigDecimal eth = new java.math.BigDecimal(wei)
                .divide(new java.math.BigDecimal("1000000000000000000"));
        return eth.toPlainString();
    }
}
