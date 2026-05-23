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

    // --- Phase 9 : tracabilite du payout ---

    /**
     * URL du justificatif uploade par le proprietaire (PDF/image) :
     * preuve du loyer percu (virement, capture Mobile Money, contrat de bail...).
     * Visible par l'admin lors de la validation.
     */
    @Column(name = "justificatif_url", length = 500)
    private String justificatifUrl;

    /**
     * L'admin a confirme visuellement que FURSA a bien recu l'argent du proprietaire.
     * GUARD : la distribution est REFUSEE tant que ce flag est false.
     * Eviter de distribuer de l'argent qu'on n'a pas encaisse.
     */
    @Column(name = "argent_recu_par_fursa", nullable = false)
    private Boolean argentRecuParFursa = false;

    // --- Phase 10b : window de declaration 1-5 + penalite retard ---

    /**
     * Penalite retenue sur le montant declare si la soumission a eu lieu apres le 5
     * du mois (hors fenetre de declaration). Va au compte central FURSA.
     * Default 0 = soumission dans les temps.
     */
    @Column(name = "penalite_retard", nullable = false, precision = 15, scale = 2)
    private BigDecimal penaliteRetard = BigDecimal.ZERO;

    /**
     * Montant net distribuable aux investisseurs = montantTotal - penaliteRetard.
     * Methode utilitaire (non-persistee) : la verite reste dans montantTotal + penaliteRetard.
     */
    @Transient
    public BigDecimal getMontantDistribuable() {
        if (montantTotal == null) return BigDecimal.ZERO;
        BigDecimal penalite = penaliteRetard == null ? BigDecimal.ZERO : penaliteRetard;
        return montantTotal.subtract(penalite);
    }
}
