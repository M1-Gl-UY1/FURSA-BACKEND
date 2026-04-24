package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
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
public class Propriete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prop")
    private Long id;

    private String nom;
    private String localisation;

    @Column(columnDefinition = "TEXT")
    private String description;
    private Integer nombreTotalPart;
    private Integer partsDisponibles;
    private BigDecimal prixUnitairePart;

    @Enumerated(EnumType.STRING)
    private StatutPropriete statut;

    private Double rentabilitePrevue;
    private String images;
    private LocalDate dateCreation;

    @Version
    private Long version;

    @OneToMany(mappedBy = "propriete", cascade = CascadeType.ALL)
    private List<Document> documents;

    @OneToMany(mappedBy = "propriete")
    private List<Revenus> revenus;

    @OneToMany(mappedBy = "propriete")
    private List<Possession> possessions;
}
