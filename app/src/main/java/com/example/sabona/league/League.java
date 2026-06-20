package com.example.sabona.league;

/**
 * Definicija svih liga u igri.
 *
 * Liga 0 (Nulta)  – početna, 0 zvezda
 * Liga 1 (Bronza) – od 100 zvezda
 * Liga 2 (Srebro) – od 200 zvezda
 * Liga 3 (Zlato)  – od 400 zvezda
 * Liga 4 (Platina)– od 800 zvezda
 * Liga 5 (Dijamant)– od 1600 zvezda
 *
 * Dnevni bonus tokeni = index lige (liga 0 → 0 bonus, liga 3 → 3 bonus)
 */
public enum League {

    NULTA(0, "Nulta liga",       "ic_league_0",  0,    0),
    BRONZA(1, "Bronzana liga",   "ic_league_1",  100,  1),
    SREBRO(2, "Srebrna liga",    "ic_league_2",  200,  2),
    ZLATO(3,  "Zlatna liga",     "ic_league_3",  400,  3),
    PLATINA(4,"Platinska liga",  "ic_league_4",  800,  4),
    DIJAMANT(5,"Dijamantska liga","ic_league_5", 1600, 5);

    public final int index;
    public final String displayName;
    /** Naziv drawable resursa za ikonu (u res/drawable/) */
    public final String iconResName;
    /** Minimalan ukupan broj zvezda potrebnih za ovu ligu */
    public final int starsRequired;
    /** Broj bonus tokena koje liga donosi svakog dana */
    public final int dailyBonusTokens;

    League(int index, String displayName, String iconResName,
           int starsRequired, int dailyBonusTokens) {
        this.index            = index;
        this.displayName      = displayName;
        this.iconResName      = iconResName;
        this.starsRequired    = starsRequired;
        this.dailyBonusTokens = dailyBonusTokens;
    }

    /**
     * Vrati ligu u kojoj igrač treba da se nalazi na osnovu ukupnih zvezda.
     * Ide od najveće prema najmanjoj – prva liga čiji prag je zadovoljen.
     */
    public static League forStars(int stars) {
        League[] values = League.values();
        for (int i = values.length - 1; i >= 0; i--) {
            if (stars >= values[i].starsRequired) {
                return values[i];
            }
        }
        return NULTA;
    }

    /** Vrati Ligu po indeksu (0–5). */
    public static League fromIndex(int index) {
        for (League l : values()) {
            if (l.index == index) return l;
        }
        return NULTA;
    }
}