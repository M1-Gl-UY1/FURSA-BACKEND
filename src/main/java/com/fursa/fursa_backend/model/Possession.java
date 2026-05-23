package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutPossession;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "possession",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_possession_investisseur_propriete",
                columnNames = {"id_inv", "id_prop"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Possession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pos")
    private Long id;

    private Integer nombreDeParts;

    @ManyToOne
    @JoinColumn(name = "id_prop")
    private Propriete propriete;

    @ManyToOne
    @JoinColumn(name = "id_inv")
    private Investisseur investisseur;

    /**
     * Phase 10c : statut de la possession.
     * PENDING tant que la collecte de la propriete n'a pas atteint 80%.
     * ACTIVE des qu'elle est FINANCEE, l'investisseur percoit alors les dividendes.
     * ANNULEE si la collecte a ete annulee, l'investisseur a ete rembourse.
     *
     * Default = PENDING pour les nouveaux achats. Les possessions historiques sont
     * migrees a ACTIVE par le script SQL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 16)
    private StatutPossession statut = StatutPossession.PENDING;

    @Version
    private Long version;
}
