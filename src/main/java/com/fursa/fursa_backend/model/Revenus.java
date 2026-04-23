package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Revenus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal montant;
    private LocalDateTime dateDistribution;

    // Correction: @ManyToOne au lieu de cibler List
    @ManyToOne
    @JoinColumn(name = "propriete_id")
    private Propriete propriete;

    // Ou si vous voulez une liste de propriétés, utilisez @ManyToMany
    @ManyToMany
    @JoinTable(
            name = "revenus_proprietes",
            joinColumns = @JoinColumn(name = "revenus_id"),
            inverseJoinColumns = @JoinColumn(name = "propriete_id")
    )
    private List<Propriete> proprietes = new ArrayList<>();
}