package com.fursa.fursa_backend.dto;

public record BlockchainResponse(
    boolean success,
    String txHash,
    String message,
    Object data
) {}