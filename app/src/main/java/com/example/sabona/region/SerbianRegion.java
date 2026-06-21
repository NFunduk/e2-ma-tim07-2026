package com.example.sabona.region;

/**
 * Definicija 6 regiona Srbije korišćenih u aplikaciji.
 *
 * Koristi PRAVU geografiju — granice svakog regiona su definisane kao
 * poligon GPS koordinata (lat, lon), koje se crtaju preko prave
 * OpenStreetMap podloge u {@link RegionsMapFragment}. Granice su
 * pojednostavljene aproksimacije (dovoljno precizne za potrebe ove
 * aplikacije), ne zvanične administrativne granice.
 *
 * Nazivi regiona su isti kao u {@code RegisterFragment} (spinner za izbor
 * regiona prilikom registracije), tako da se "region" String iz korisničkog
 * dokumenta direktno mapira na ovaj enum.
 */
public enum SerbianRegion {

    VOJVODINA(
            "Vojvodina",
            "🌾",
            0xFF6BA368,
            new double[][]{
                    {46.18, 18.83}, {46.18, 21.60}, {45.13, 21.60},
                    {44.85, 20.80}, {44.90, 19.40}, {45.20, 18.95}
            }
    ),
    BEOGRAD(
            "Beograd",
            "🏛️",
            0xFF426BC2,
            new double[][]{
                    {44.95, 20.25}, {44.95, 20.65}, {44.65, 20.65},
                    {44.60, 20.30}
            }
    ),
    SUMADIJA(
            "Šumadija",
            "⛰️",
            0xFF8FB3FF,
            new double[][]{
                    {44.60, 20.30}, {44.65, 21.20}, {43.95, 21.30},
                    {43.85, 20.60}, {44.20, 20.15}
            }
    ),
    ZAPADNA_SRBIJA(
            "Zapadna Srbija",
            "🌲",
            0xFFCD7F32,
            new double[][]{
                    {44.85, 19.10}, {44.60, 20.30}, {44.20, 20.15},
                    {43.55, 19.80}, {43.30, 19.30}, {43.60, 18.85},
                    {44.30, 19.00}
            }
    ),
    JUZNA_I_ISTOCNA_SRBIJA(
            "Južna i Istočna Srbija",
            "🏔️",
            0xFFCE93D8,
            new double[][]{
                    {44.65, 21.20}, {44.85, 22.60}, {43.85, 23.00},
                    {42.95, 22.45}, {42.65, 21.85}, {43.30, 21.05},
                    {43.95, 21.30}
            }
    ),
    KOSOVO_I_METOHIJA(
            "Kosovo i Metohija",
            "🕊️",
            0xFFBDBDBD,
            new double[][]{
                    {43.30, 19.95}, {43.30, 21.05}, {42.65, 21.85},
                    {42.10, 21.10}, {42.10, 20.30}, {42.55, 19.95}
            }
    );

    /** Naziv regiona — identičan stringu sačuvanom u users/{uid}.region */
    public final String displayName;
    /** Emoji ikonica koja proizvoljno predstavlja region (tačka d. iz specifikacije) */
    public final String icon;
    /** Akcentna boja regiona (koristi se za bojenje zone na mapi i marker tačke) */
    public final int color;
    /** Granica regiona kao niz [lat, lon] tačaka (poligon), pojednostavljena aproksimacija */
    public final double[][] boundary;

    SerbianRegion(String displayName, String icon, int color, double[][] boundary) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.boundary = boundary;
    }

    /**
     * Pronađi region po nazivu sačuvanom u Firestore-u.
     * Vraća {@code null} ako igrač nema postavljen / prepoznat region.
     */
    public static SerbianRegion fromDisplayName(String name) {
        if (name == null) return null;
        for (SerbianRegion r : values()) {
            if (r.displayName.equalsIgnoreCase(name.trim())) return r;
        }
        return null;
    }
}