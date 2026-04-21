package com.fursa.fursa_backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BlockchainService {

    private static BlockchainService instance;

    @Value("${blockchain.rpc.url}")
    private String rpcUrl;

    private final ConcurrentHashMap<String, TransactionStatus> pendingTransactions = new ConcurrentHashMap<>();

    private BlockchainService() {}

    public static synchronized BlockchainService getInstance() {
        if (instance == null) {
            instance = new BlockchainService();
        }
        return instance;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing BlockchainService with RPC: {}", rpcUrl);
    }

    public String sendTransaction(String walletAddress, Double amount) {
        String txHash = generateTxHash(walletAddress, amount);
        pendingTransactions.put(txHash, TransactionStatus.PENDING);
        log.info("Transaction sent: {} for {} ETH to {}", txHash, amount, walletAddress);
        simulateBlockchainSubmission(txHash);
        return txHash;
    }

    public boolean waitForConfirmation(String txHash) {
        try {
            Thread.sleep(3000);
            TransactionStatus status = pendingTransactions.get(txHash);
            boolean confirmed = status == TransactionStatus.CONFIRMED;
            if (confirmed) log.info("Transaction confirmed: {}", txHash);
            return confirmed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void assignSharesOnChain(String walletAddress, String propertyId, Integer parts) {
        log.info("Assigning {} parts of property {} to {}", parts, propertyId, walletAddress);
        String txHash = generateTxHash(walletAddress, 0.0);
        pendingTransactions.put(txHash, TransactionStatus.CONFIRMED);
    }

    public String lockSharesOnChain(String walletAddress, String propertyId, Integer parts) {
        String lockId = "lock_" + System.currentTimeMillis();
        log.info("Locking {} parts of property {} for {}", parts, propertyId, walletAddress);
        return lockId;
    }

    public void unlockShares(String lockId) {
        log.info("Unlocking shares: {}", lockId);
    }

    public void relockShares(String lockId) {
        log.info("Relocking shares: {}", lockId);
    }

    public void reverseTransaction(String txHash) {
        log.info("Reversing transaction: {}", txHash);
        pendingTransactions.put(txHash, TransactionStatus.REVERSED);
    }

    private String generateTxHash(String walletAddress, Double amount) {
        return "0x" + Integer.toHexString((walletAddress + amount + System.currentTimeMillis()).hashCode());
    }

    private void simulateBlockchainSubmission(String txHash) {
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                pendingTransactions.put(txHash, TransactionStatus.CONFIRMED);
                log.info("Blockchain confirmed: {}", txHash);
            } catch (InterruptedException e) {
                pendingTransactions.put(txHash, TransactionStatus.FAILED);
            }
        }).start();
    }

    public enum TransactionStatus {
        PENDING, CONFIRMED, FAILED, REVERSED
    }
}