package com.fursa.fursa_backend.feature.command;

public interface Command {
    void execute();
    void undo();
    String getTransactionHash();
}