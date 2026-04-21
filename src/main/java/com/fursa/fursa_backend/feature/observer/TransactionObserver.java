// TransactionObserver.java - Corrigé
package com.fursa.fursa_backend.feature.observer;

import com.fursa.fursa_backend.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionObserver implements Observer {

    private final TransactionHistoryService transactionHistoryService;

    @Override
    public void update(String event, Object data) {
        if ("TRANSACTION_COMPLETED".equals(event)) {
            transactionHistoryService.recordTransaction(data);
        }
    }
}