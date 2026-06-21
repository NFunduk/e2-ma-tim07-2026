package com.example.sabona.region;

/**
 * Agregirane statistike za jedan region (tačka d. iz specifikacije
 * "Prikaz regiona"):
 *  - broj trenutno aktivnih (online) igrača
 *  - broj ukupno registrovanih igrača
 *
 * Polja za broj osvojenih 1./2./3. mesta na regionalnoj rang listi
 * NISU uključena ovde — ta funkcionalnost (mesečna rang lista po
 * regionima, raspodela nagrada) je deo zadatka "4. Rang lista" i
 * "5b/5e Prikaz regiona", koji se radi naknadno kada rang liste budu
 * implementirane. Kada taj deo postoji, ovde se može dodati polje
 * {@code podiumFinishes} bez menjanja ostatka ovog ekrana.
 */
public class RegionStats {

    public final SerbianRegion region;
    public final int activePlayers;
    public final int totalPlayers;

    public RegionStats(SerbianRegion region, int activePlayers, int totalPlayers) {
        this.region = region;
        this.activePlayers = activePlayers;
        this.totalPlayers = totalPlayers;
    }
}