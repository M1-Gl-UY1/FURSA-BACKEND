package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Dividende {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_div")
    private Long id;

    private BigDecimal montantCalcule;
    private LocalDate dateDistribution;

    @Enumerated(EnumType.STRING)
    private StatutPaiement statut;

    @Column(unique = true)
    private String hashTransaction;

    @ManyToOne
    @JoinColumn(name = "id_rev")
    private Revenus revenus;

    @ManyToOne
    @JoinColumn(name = "id_inv")
    private Investisseur investisseur;

    // --- Phase 9 : tracabilite du payout effectif ---

    /**
     * Date a laquelle l'admin a confirme le versement reel a l'investisseur.
     * Null tant que statut = VALIDE. Set quand statut passe a PAYE.
     */
    @Column(name = "date_paiement_effectif")
    private LocalDate datePaiementEffectif;

    /**
     * Reference / preuve du paiement effectif (numero de virement, tx blockchain reelle,
     * reference Mobile Money). Permet a l'investisseur de tracer son versement.
     */
    @Column(name = "preuve_paiement", length = 500)
    private String preuvePaiement;

    /**
     * Methode utilisee pour le versement final : MOBILE_MONEY / VIREMENT / CRYPTO / AUTRE.
     */
    @Column(name = "methode_paiement", length = 30)
    private String methodePaiement;
}
