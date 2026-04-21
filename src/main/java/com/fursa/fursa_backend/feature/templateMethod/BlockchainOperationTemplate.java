// BlockchainOperationTemplate.java - Changement: private -> protected
package com.fursa.fursa_backend.feature.templateMethod;

import com.fursa.fursa_backend.service.BlockchainService;
import com.fursa.fursa_backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BlockchainOperationTemplate {

    protected final BlockchainService blockchainService;
    protected final NotificationService notificationService;
    protected final Logger log = LoggerFactory.getLogger(getClass());  // Ajouter cette ligne

    public BlockchainOperationTemplate(BlockchainService blockchainService, NotificationService notificationService) {
        this.blockchainService = blockchainService;
        this.notificationService = notificationService;
    }

    protected abstract void validate();
    protected abstract String executeOnBlockchain();
    protected abstract void updateDatabase(String txHash);
    protected abstract void notifySuccess();

    public final void execute() {
        validate();
        String txHash = executeOnBlockchain();
        boolean confirmed = blockchainService.waitForConfirmation(txHash);
        if (confirmed) {
            updateDatabase(txHash);
            notifySuccess();
            log.info("Operation completed successfully: {}", txHash);
        } else {
            log.error("Blockchain transaction failed: {}", txHash);
            throw new RuntimeException("Transaction failed");
        }
    }
}