package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Dividende {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_div")
    private Long id;

    private BigDecimal montantCalcule;
    private LocalDate dateDistribution;

    @Enumerated(EnumType.STRING)
    private StatutPaiement statut;

    @Column(unique = true)
    private String hashTransaction;

    @ManyToOne
    @JoinColumn(name = "id_rev")
    private Revenus revenus;

    @ManyToOne
    @JoinColumn(name = "id_inv")
    private Investisseur investisseur;
}
