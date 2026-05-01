package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutRevenu;
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
    private Propriete propriete;

    @OneToMany(mappedBy = "revenus", cascade = CascadeType.ALL)
    private List<Dividende> dividendes;

    // --- Phase 8 : workflow déclaration propriétaire ---

    /** ID de l'investisseur (propriétaire) qui a déclaré ce revenu. Null = créé directement par admin. */
    private Long proposeurId;

    @Enumerated(EnumType.STRING)
    private StatutRevenu statut;

    /** Motif du refus si statut = REFUSE. */
    @Column(columnDefinition = "TEXT")
    private String motifRefus;

    private LocalDate periodeDebut;
    private LocalDate periodeFin;
}
