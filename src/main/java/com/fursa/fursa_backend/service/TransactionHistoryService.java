// TransactionHistoryService.java - À créer
package com.fursa.fursa_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionHistoryService {

    public void recordTransaction(Object data) {
        log.info("Recording transaction: {}", data);
        // Implémentation pour enregistrer la transaction
    }
}