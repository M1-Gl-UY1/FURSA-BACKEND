package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.feature.command.Command;
import com.fursa.fursa_backend.model.Transaction;
import com.fursa.fursa_backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Stack;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final Stack<Command> commandHistory = new Stack<>();

    public void executeCommand(Command command) {
        try {
            command.execute();
            commandHistory.push(command);
            log.info("Command executed: {}", command.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Command execution failed: {}", e.getMessage());
            throw new RuntimeException("Command execution failed: " + e.getMessage());
        }
    }

    public void undoLastCommand() {
        if (!commandHistory.isEmpty()) {
            Command command = commandHistory.pop();
            command.undo();
            log.info("Command undone: {}", command.getClass().getSimpleName());
        } else {
            log.warn("No command to undo");
        }
    }

    public Transaction createTransaction(String txHash, Double amount) {
        Transaction transaction = new Transaction();
        transaction.setHashTransaction(txHash);
        transaction.setMontant(amount);
        transaction.setDateTransaction(LocalDateTime.now());
        transaction.setTypeOperation("INVESTMENT");
        return transactionRepository.save(transaction);
    }

    public Transaction createTransaction(String txHash, Double amount, Integer nombreParts,
                                         Long acheteurId, Long vendeurId, Long proprieteId) {
        Transaction transaction = new Transaction();
        transaction.setHashTransaction(txHash);
        transaction.setMontant(amount);
        transaction.setNombreParts(nombreParts);
        transaction.setTypeOperation("SECONDARY_MARKET");
        transaction.setDateTransaction(LocalDateTime.now());
        return transactionRepository.save(transaction);
    }

    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id).orElse(null);
    }

    public void updateTransactionStatus(String id, String status) {
        // Implementation
    }
}