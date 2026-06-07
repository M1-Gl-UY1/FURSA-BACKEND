package com.fursa.fursa_backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * V2 M (07/06/2026) : vue complete admin du prix dynamique d'un bien.
 *
 * Permet d'afficher dans une page unique : la formule, les valeurs courantes
 * substituees, les bornes (plancher / plafond), les constantes du modele
 * et l'historique des recalculs.
 *
 * Voir PRIX_DYNAMIQUE_FURSA.md pour la specification metier.
 */
public record PrixPartDiagnosticResponse(
        Long proprieteId,
        String proprieteNom,
        /** V2 O : "V1" (legacy immuable) | "V2" (sync on-chain) | null si pas tokenise. */
        String contratVersion,
        /** Adresse du contrat on-chain (null si pas tokenise). */
        String adresseContrat,
        /** Hash de la tx de deploiement (null si pas tokenise). */
        String transactionHash,
        BigDecimal prixInitial,
        BigDecimal prixCourant,
        /** Variation prix_courant / prix_initial - 1, en % (ex: 12.50 = +12.5%). */
        BigDecimal variationPct,
        /** Bonus rentabilite cumule (fraction : 0.05 = +5%). */
        BigDecimal bonusRentabiliteTotal,
        /** Bonus demande instantane (fraction : 0.10 = +10%). */
        BigDecimal bonusDemande,
        /** Borne basse absolue (prix_initial * 50%). */
        BigDecimal plancher,
        /** Borne haute absolue (prix_initial * 200%). */
        BigDecimal plafond,
        /** Rentabilite annuelle prevue annoncee a Hugh (ex: 8.0 = 8%/an). */
        Double rentabilitePrevue,
        BigDecimal prixVenteTotal,
        Integer fractionVenduePct,
        /** prix_vente_total * fraction_vendue / 100 — base de calcul de la rentabilite reelle. */
        BigDecimal valeurMisEnVente,
        ConstantesFormule constantes,
        List<HistoriquePrixPartResponse> historique
) {
    /** Constantes du modele exposees pour les afficher dans l'UI (source unique : PrixPartService). */
    public record ConstantesFormule(
            BigDecimal lissageRentabilite,
            BigDecimal capContributionTrimestrielle,
            BigDecimal capBonusRentabiliteTotal,
            BigDecimal coefDemande,
            BigDecimal capBonusDemande,
            BigDecimal plancherPrixPct,
            BigDecimal plafondPrixPct
    ) {}
}
