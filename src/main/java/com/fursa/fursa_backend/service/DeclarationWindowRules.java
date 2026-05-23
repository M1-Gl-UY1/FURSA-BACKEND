package com.fursa.fursa_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Phase 10b : regles metier pour la fenetre de declaration des revenus.
 *
 * - Fenetre normale : du 1er au 5 du mois (inclus), pour declarer le mois N-1
 * - Hors fenetre (jour > 5) : declaration autorisee mais PENALITE_RETARD_EUR
 *   est retenue sur le montant declare et reversee au compte central FURSA
 * - Pas de date limite supreme : le proprietaire peut toujours declarer (mais paie)
 */
public final class DeclarationWindowRules {

    /** Jour limite (inclus) de la fenetre normale, sans penalite. */
    public static final int JOUR_FERMETURE_FENETRE = 5;

    /** Penalite forfaitaire si declaration apres le 5 du mois. */
    public static final BigDecimal PENALITE_RETARD_EUR = new BigDecimal("300.00");

    private DeclarationWindowRules() {}

    /** True si la date donnee est dans la fenetre normale (jour <= 5). */
    public static boolean estDansFenetre(LocalDate date) {
        return date.getDayOfMonth() <= JOUR_FERMETURE_FENETRE;
    }

    /** Penalite a appliquer pour une declaration soumise a {date}. */
    public static BigDecimal penaliteApplicable(LocalDate dateSoumission, BigDecimal montantDeclare) {
        if (estDansFenetre(dateSoumission)) return BigDecimal.ZERO;
        if (montantDeclare == null || montantDeclare.signum() <= 0) return BigDecimal.ZERO;
        // La penalite ne peut pas exceder le montant declare (cap).
        return PENALITE_RETARD_EUR.min(montantDeclare);
    }

    /** Mois precedent celui de la date donnee (i.e. ce qu'on doit declarer). */
    public static YearMonth moisADeclarer(LocalDate date) {
        return YearMonth.from(date).minusMonths(1);
    }

    /** Nombre de jours restants avant la fermeture de la fenetre. Negatif si fermee. */
    public static int joursRestantsAvantFermeture(LocalDate date) {
        return JOUR_FERMETURE_FENETRE - date.getDayOfMonth();
    }
}
