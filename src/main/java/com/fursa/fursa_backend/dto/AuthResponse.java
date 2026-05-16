package com.fursa.fursa_backend.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String refreshToken;
    private String type;
    private long expiresIn;
}
