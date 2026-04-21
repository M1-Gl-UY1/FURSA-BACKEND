package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Revenus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rev")
    private Long id;

    private LocalDate date;

    private BigDecimal montantTotal;

    @ManyToOne
    @JoinColumn(name = "id_prop")
    private List<Propriete> proprietes;

    @OneToMany(mappedBy = "revenus", cascade = CascadeType.ALL)
    private List<Dividende> dividendes;
}
