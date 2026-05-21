package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "devise_rate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviseRate {

    @Id
    @Column(name = "code_devise", length = 3, nullable = false)
    private String codeDevise;

    @Column(name = "taux_vers_usdc", nullable = false, precision = 38, scale = 18)
    private BigDecimal tauxVersUsdc;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
