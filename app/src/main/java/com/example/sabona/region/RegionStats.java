package com.example.sabona.region;

/**
 * Agregirane statistike za jedan region (specifikacija "5. Prikaz
 * regiona", tačka d.):
 *  - broj trenutno aktivnih (online) igrača (d.ii)
 *  - broj ukupno registrovanih igrača (d.iii)
 *  - broj osvojenih 1./2./3. mesta na mesečnoj rang listi po regionima,
 *    kumulativno kroz sve arhivirane cikluse (d.i)
 *  - ukupan broj zvezda osvojenih u TRENUTNOM mesečnom ciklusu (5.b)
 */
public class RegionStats {

    public final SerbianRegion region;
    public final int activePlayers;
    public final int totalPlayers;

    /** Ukupno zvezda svih igrača ovog regiona u trenutnom mesečnom ciklusu (5.b). */
    public final long monthlyStarsCurrentCycle;

    /** Kumulativni broj puta kad je region bio 1./2./3. na mesečnoj rang listi (5.d.i). */
    public final int firstPlaceFinishes;
    public final int secondPlaceFinishes;
    public final int thirdPlaceFinishes;

    public RegionStats(SerbianRegion region, int activePlayers, int totalPlayers,
                       long monthlyStarsCurrentCycle,
                       int firstPlaceFinishes, int secondPlaceFinishes, int thirdPlaceFinishes) {
        this.region = region;
        this.activePlayers = activePlayers;
        this.totalPlayers = totalPlayers;
        this.monthlyStarsCurrentCycle = monthlyStarsCurrentCycle;
        this.firstPlaceFinishes = firstPlaceFinishes;
        this.secondPlaceFinishes = secondPlaceFinishes;
        this.thirdPlaceFinishes = thirdPlaceFinishes;
    }
}