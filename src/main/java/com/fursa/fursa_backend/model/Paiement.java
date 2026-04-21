package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.TypePaiement;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Paiement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paie")
    private Long id;

    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    private TypePaiement type;

    @Enumerated(EnumType.STRING)
    private StatutPaiement statut;

    private LocalDateTime date;
    private Integer nombre_parts;

    @ManyToOne
    @JoinColumn(name = "id_prop")
    private Propriete propriete;

    @ManyToOne
    @JoinColumn(name = "id_inv")
    private Investisseur investisseur;

    @OneToMany(mappedBy = "paiement", cascade = CascadeType.ALL)
    private List<Transaction> transactions;
}
