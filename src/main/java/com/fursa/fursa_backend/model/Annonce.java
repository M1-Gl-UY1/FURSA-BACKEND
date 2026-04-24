package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Annonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal prixUnitaireDemande;

    @Enumerated(EnumType.STRING)
    private StatutAnnonce statut;

    private Integer nombreDePartsAVendre;

    @ManyToOne
    @JoinColumn(name = "id_inv")
    private Investisseur investisseur;

    @ManyToOne
    @JoinColumn(name = "id_prop")
    private Propriete propriete;

    @Version
    private Long version;
}
