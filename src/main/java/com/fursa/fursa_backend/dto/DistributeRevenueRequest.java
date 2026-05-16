package com.fursa.fursa_backend.dto;

import java.util.List;

// DistributeRevenueRequest.java
public record DistributeRevenueRequest(
    double amountEth,
    List<String> investorAddresses,
    List<Integer> shares
) {}