package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutTransaction;
import com.fursa.fursa_backend.model.enumeration.TypeOperation;
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

    @Enumerated(EnumType.STRING)
    private TypeOperation typeOperation;

    private Integer nombreParts;
    private BigDecimal montant;

    private LocalDateTime dateTransaction;

    @Enumerated(EnumType.STRING)
    private StatutTransaction statut;

    @ManyToOne
    @JoinColumn(name = "id_paie")
    private Paiement paiement;
}
