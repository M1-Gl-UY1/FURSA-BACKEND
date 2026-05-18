package com.fursa.fursa_backend.dto;

public record AddInvestorRequest(
    String investorAddress,
    int shares
) {}