package com.fursa.fursa_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Phase 10b + ajustement Hugh 22/05/2026 : regles metier pour la fenetre de
 * declaration TRIMESTRIELLE des revenus.
 *
 * - 4 trimestres standard : Q1 (jan-fev-mar), Q2 (avr-mai-jun), Q3 (jui-aou-sep), Q4 (oct-nov-dec)
 * - Fenetre normale : du 1er au 15 du 1er mois du trimestre N+1 (pour declarer
 *   les revenus du trimestre N qui vient de se terminer).
 *   Ex : Q1 (jan-fev-mar) se declare entre le 1er et le 15 avril.
 * - Hors fenetre (jour > 15) : declaration autorisee mais PENALITE_RETARD_USD
 *   est retenue sur le montant declare et reversee au compte central FURSA.
 * - Pas de date limite supreme : le proprietaire peut toujours declarer (mais paie).
 */
public final class DeclarationWindowRules {

    /** Jour limite (inclus) de la fenetre normale, sans penalite. */
    public static final int JOUR_FERMETURE_FENETRE = 15;

    /** Penalite forfaitaire si declaration apres le 15. */
    public static final BigDecimal PENALITE_RETARD_USD = new BigDecimal("300.00");

    /** Alias pour retro-compatibilite. A supprimer en V2. */
    @Deprecated
    public static final BigDecimal PENALITE_RETARD_EUR = PENALITE_RETARD_USD;

    private DeclarationWindowRules() {}

    /**
     * True si la date donnee est dans la fenetre normale ET si le mois est le
     * 1er mois d'un trimestre (janvier, avril, juillet, octobre).
     * Cf reunion Hugh : declaration trimestrielle, pas mensuelle.
     */
    public static boolean estDansFenetre(LocalDate date) {
        int mois = date.getMonthValue();
        boolean moisOuverture = (mois == 1 || mois == 4 || mois == 7 || mois == 10);
        return moisOuverture && date.getDayOfMonth() <= JOUR_FERMETURE_FENETRE;
    }

    /** Penalite a appliquer pour une declaration soumise a {date}. */
    public static BigDecimal penaliteApplicable(LocalDate dateSoumission, BigDecimal montantDeclare) {
        if (estDansFenetre(dateSoumission)) return BigDecimal.ZERO;
        if (montantDeclare == null || montantDeclare.signum() <= 0) return BigDecimal.ZERO;
        return PENALITE_RETARD_USD.min(montantDeclare);
    }

    /**
     * Trimestre a declarer = le trimestre PRECEDENT (qui vient de se terminer).
     * Ex : le 5 avril 2026 -> on declare Q1 2026 (janvier-mars).
     * Ex : le 12 fevrier 2026 -> on declare Q4 2025 (oct-dec 2025).
     */
    public static YearQuarter trimestreADeclarer(LocalDate date) {
        return YearQuarter.from(date).previous();
    }

    /**
     * Jours restants avant la fermeture de la fenetre.
     * Negatif si la fenetre est deja fermee, ou si nous ne sommes pas dans un
     * mois d'ouverture (janvier, avril, juillet, octobre).
     */
    public static int joursRestantsAvantFermeture(LocalDate date) {
        int mois = date.getMonthValue();
        if (mois != 1 && mois != 4 && mois != 7 && mois != 10) {
            // Pas dans un mois d'ouverture : fenetre fermee. Calc le nombre de jours
            // jusqu'au prochain 1er du mois d'ouverture (info utile pour l'UI).
            return -1 * joursDepuisFermeture(date);
        }
        return JOUR_FERMETURE_FENETRE - date.getDayOfMonth();
    }

    /**
     * Nombre de jours ecoules depuis la fermeture de la derniere fenetre
     * (positif si on est en retard, 0 si pile a la fermeture).
     */
    public static int joursDepuisFermeture(LocalDate date) {
        // Date la plus recente du 16 d'un mois d'ouverture (janvier, avril, juillet, octobre)
        LocalDate derniereFermeture = derniereFermetureAvant(date);
        return (int) java.time.temporal.ChronoUnit.DAYS.between(derniereFermeture, date);
    }

    private static LocalDate derniereFermetureAvant(LocalDate date) {
        int annee = date.getYear();
        int mois = date.getMonthValue();
        // Mois d'ouverture les plus proches : on cherche le precedent 16 d'un trimestre.
        int[] moisOuverture = {10, 7, 4, 1};
        for (int m : moisOuverture) {
            if (m < mois || (m == mois && date.getDayOfMonth() >= JOUR_FERMETURE_FENETRE + 1)) {
                return LocalDate.of(annee, m, JOUR_FERMETURE_FENETRE + 1);
            }
        }
        // Tous les mois d'ouverture de l'annee courante sont posterieurs -> on prend Q4 annee precedente
        return LocalDate.of(annee - 1, 10, JOUR_FERMETURE_FENETRE + 1);
    }

    /**
     * Alias retro-compat : retourne le MOIS a declarer (utilise par l'ancien code
     * Phase 10b). Remplace par trimestreADeclarer() en V2.
     */
    @Deprecated
    public static YearMonth moisADeclarer(LocalDate date) {
        return trimestreADeclarer(date).debut();
    }
}
