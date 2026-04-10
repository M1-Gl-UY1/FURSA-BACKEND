package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_trans")
    private Long id;

    @Column(unique = true)
    private String hashTransaction;
    private String typeOperation;

    private Integer nombreParts;
    private BigDecimal montant;

    private LocalDateTime dateTransaction;

    @ManyToOne
    @JoinColumn(name = "id_paie")
    private Paiement paiement;
}
