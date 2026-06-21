package com.example.sabona.region;

/**
 * Jedan red u rang listi regiona za trenutni mesečni ciklus
 * (specifikacija 5.b — "ukupan broj osvojenih zvezda u toku trajanja
 * mesečnog ciklusa za sve igrače koji pripadaju tom regionu").
 */
public class RegionMonthlyRankEntry {

    public final SerbianRegion region;
    public final long totalStars;

    public RegionMonthlyRankEntry(SerbianRegion region, long totalStars) {
        this.region = region;
        this.totalStars = totalStars;
    }
}