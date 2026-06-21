package com.example.sabona.region;

/**
 * Jedan arhiviran zapis: koji region je osvojio koje mesto (1./2./3.)
 * na kraju jednog mesečnog ciklusa.
 *
 * Čuva se u Firestore kolekciji "regionCycleHistory" PRE poziva
 * {@code LeaderboardRepository.resetMonthlyCycle()} (koji briše
 * monthlyStars na 0) — bez ovog snapshot-a, podatak "ko je bio prvi
 * prošli ciklus" bi se trajno izgubio.
 *
 * Koristi se za:
 *  - specifikacija 5.d.i — kumulativni broj osvojenih 1./2./3. mesta po
 *    regionu (broji se koliko puta se region pojavljuje na position=1/2/3
 *    kroz sve arhivirane cikluse)
 *  - specifikacija 5.e — okvir avatara u boji (zlatna/srebrna/bronzana)
 *    za igrače čiji region je bio 1./2./3. na NAJSKORIJEM (prethodnom)
 *    ciklusu
 */
public class RegionCycleResult {

    public final String regionName; // SerbianRegion.displayName
    public final int position;      // 1, 2 ili 3
    public final int totalStars;    // ukupno osvojenih zvezda regiona u tom ciklusu
    public final long cycleEndMillis; // kada je ciklus zaključen (System.currentTimeMillis())

    public RegionCycleResult(String regionName, int position, int totalStars, long cycleEndMillis) {
        this.regionName = regionName;
        this.position = position;
        this.totalStars = totalStars;
        this.cycleEndMillis = cycleEndMillis;
    }
}