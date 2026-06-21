package com.example.sabona.league;

/**
 * Sva poslovna logika vezana za napredovanje kroz lige.
 *
 * Ova klasa je čisto poslovna logika (bez Firestore/Android zavisnosti)
 * i može se lako testirati.
 */
public class LeagueManager {


    //  KONSTANTE
    /** Procenat zvezda koje igrač gubi ako se ne plasira na mesečnoj rang listi */
    private static final float MONTHLY_PENALTY_PERCENT = 0.30f;

    /** Broj baznih tokena koje svaki igrač dobija svakog dana */
    public static final int BASE_DAILY_TOKENS = 5;

    //  IZRAČUNAVANJA                                                       //

    /**
     * Odredi u kojoj ligi igrač treba da se nalazi.
     * Pozivati uvijek nakon promjene zvezda.
     */
    public static League computeLeague(int stars) {
        return League.forStars(stars);
    }

    /**
     * Ukupan broj tokena koji igrač dobija svakog dana:
     *   BASE (5) + bonus koji donosi trenutna liga.
     *
     * @param currentLeague liga u kojoj se igrač trenutno nalazi
     */
    public static int dailyTokens(League currentLeague) {
        return BASE_DAILY_TOKENS + currentLeague.dailyBonusTokens;
    }

    /**
     * Smanji zvezde za mesečnu kaznu (30%) i vrati novi broj.
     * Primjer: 430 zvezda → 430 - 129 = 301 zvezda.
     *
     * @param currentStars trenutni ukupni broj zvezda
     * @return novi (manji) broj zvezda
     */
    public static int applyMonthlyPenalty(int currentStars) {
        int penalty = (int) (currentStars * MONTHLY_PENALTY_PERCENT);
        return Math.max(0, currentStars - penalty);
    }

    /**
     * Dodaj zvezde (pobjeda/igra) i vrati novi broj.
     * Garantuje da ne ide ispod nule.
     */
    public static int addStars(int currentStars, int delta) {
        return Math.max(0, currentStars + delta);
    }

    // ------------------------------------------------------------------ //
    //  HELPER: provjera promjene lige                                      //
    // ------------------------------------------------------------------ //

    /**
     * Rezultat operacije promjene liga — koristi se da UI zna šta da prikaže.
     */
    public static class LeagueChangeResult {
        public final League oldLeague;
        public final League newLeague;
        public final int    newStars;
        /** true = igrač je napredovao, false = pao, null = nema promjene */
        public final Boolean promoted; // null = bez promjene

        public LeagueChangeResult(League oldLeague, League newLeague,
                                  int newStars) {
            this.oldLeague = oldLeague;
            this.newLeague = newLeague;
            this.newStars  = newStars;

            if (newLeague.index > oldLeague.index) {
                this.promoted = true;
            } else if (newLeague.index < oldLeague.index) {
                this.promoted = false;
            } else {
                this.promoted = null; // nema promjene
            }
        }

        public boolean leagueChanged() {
            return promoted != null;
        }
    }

    /**
     * Primijeni promjenu zvezda i izračunaj novu ligu.
     *
     * @param currentStars    trenutne zvezde
     * @param currentLeague   trenutna liga
     * @param starsDelta      pozitivan (dobitak) ili negativan (gubitak)
     * @return rezultat s novim vrijednostima i info o promjeni lige
     */
    public static LeagueChangeResult applyStarChange(int currentStars,
                                                     League currentLeague,
                                                     int starsDelta) {
        int newStars = addStars(currentStars, starsDelta);
        League newLeague = computeLeague(newStars);
        return new LeagueChangeResult(currentLeague, newLeague, newStars);
    }

    /**
     * Primijeni mesečnu kaznu i izračunaj novu ligu.
     */
    public static LeagueChangeResult applyMonthlyPenalty(int currentStars,
                                                         League currentLeague) {
        int newStars = applyMonthlyPenalty(currentStars);
        League newLeague = computeLeague(newStars);
        return new LeagueChangeResult(currentLeague, newLeague, newStars);
    }

    /**
     * Broj zvezda potrebnih za sljedeću ligu (0 ako je igrač već u najvišoj).
     */
    public static int starsToNextLeague(int currentStars) {
        League current = computeLeague(currentStars);
        if (current == League.DIJAMANT) return 0;
        League next = League.fromIndex(current.index + 1);
        return Math.max(0, next.starsRequired - currentStars);
    }

    /**
     * Progres unutar trenutne lige kao float [0.0, 1.0].
     * Koristi se za ProgressBar na UI-u.
     */
    public static float leagueProgress(int currentStars) {
        League current = computeLeague(currentStars);
        if (current == League.DIJAMANT) return 1.0f;

        League next = League.fromIndex(current.index + 1);
        int range   = next.starsRequired - current.starsRequired;
        int earned  = currentStars - current.starsRequired;

        if (range <= 0) return 1.0f;
        return Math.min(1.0f, (float) earned / range);
    }
}