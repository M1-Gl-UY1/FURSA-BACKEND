package com.fursa.fursa_backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * P3 (Hugh 22/05/2026) : value object representant un trimestre fiscal.
 *
 * Format affiche : "2026-Q1", "2026-Q2", etc.
 * Java standard ne fournit pas YearQuarter, on l'implemente nous-meme.
 *
 * Q1 = janvier-fevrier-mars
 * Q2 = avril-mai-juin
 * Q3 = juillet-aout-septembre
 * Q4 = octobre-novembre-decembre
 */
public final class YearQuarter implements Comparable<YearQuarter> {

    private final int year;
    /** 1, 2, 3 ou 4 */
    private final int quarter;

    private YearQuarter(int year, int quarter) {
        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("Quarter doit etre entre 1 et 4 : " + quarter);
        }
        this.year = year;
        this.quarter = quarter;
    }

    public static YearQuarter of(int year, int quarter) {
        return new YearQuarter(year, quarter);
    }

    public static YearQuarter from(LocalDate date) {
        int q = ((date.getMonthValue() - 1) / 3) + 1;
        return new YearQuarter(date.getYear(), q);
    }

    public int year() { return year; }
    public int quarter() { return quarter; }

    /** Premier mois du trimestre (janvier, avril, juillet, octobre). */
    public YearMonth debut() {
        int mois = (quarter - 1) * 3 + 1;
        return YearMonth.of(year, mois);
    }

    /** Dernier mois du trimestre (mars, juin, septembre, decembre). */
    public YearMonth fin() {
        int mois = quarter * 3;
        return YearMonth.of(year, mois);
    }

    public LocalDate premierJour() {
        return debut().atDay(1);
    }

    public LocalDate dernierJour() {
        return fin().atEndOfMonth();
    }

    /** Le trimestre precedent (utilise pour "trimestre a declarer"). */
    public YearQuarter previous() {
        if (quarter == 1) return new YearQuarter(year - 1, 4);
        return new YearQuarter(year, quarter - 1);
    }

    /** Le trimestre suivant. */
    public YearQuarter next() {
        if (quarter == 4) return new YearQuarter(year + 1, 1);
        return new YearQuarter(year, quarter + 1);
    }

    /** Format ISO-like : "2026-Q1" */
    @Override
    public String toString() {
        return year + "-Q" + quarter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YearQuarter)) return false;
        YearQuarter that = (YearQuarter) o;
        return year == that.year && quarter == that.quarter;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, quarter);
    }

    @Override
    public int compareTo(YearQuarter o) {
        if (year != o.year) return Integer.compare(year, o.year);
        return Integer.compare(quarter, o.quarter);
    }
}
