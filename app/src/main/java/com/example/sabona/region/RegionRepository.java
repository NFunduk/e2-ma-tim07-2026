package com.example.sabona.region;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sve Firestore operacije vezane za prikaz regiona (zadatak "5. Prikaz
 * regiona" iz specifikacije).
 *
 * Čita podatke o zvezdama/ciklusima koje upisuje koleginičin deo
 * (4. Rang lista — polje "monthlyStars" na users/{uid}, i kolekcija
 * "regionCycleHistory" koju upisuje {@link RegionCycleService} PRE
 * njenog reseta ciklusa). Ne menja ništa u njenoj logici, samo agregira
 * njene podatke po regionu.
 */
public class RegionRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface MapPointsCallback {
        void onSuccess(List<RegionMapPoint> points);
        void onError(String message);
    }

    public interface StatsCallback {
        void onSuccess(Map<SerbianRegion, RegionStats> statsByRegion);
        void onError(String message);
    }

    public interface MonthlyRankingCallback {
        void onSuccess(List<RegionMonthlyRankEntry> ranking);
        void onError(String message);
    }

    public interface LastCycleResultCallback {
        /** @param result null ako region nije bio u top 3 na poslednjem arhiviranom ciklusu (ili arhiva ne postoji još) */
        void onSuccess(RegionCycleResult result);
        void onError(String message);
    }

    /**
     * Učitaj tačke za mapu — jednu po svakom registrovanom igraču koji ima
     * postavljen region. Ako igrač još nema sačuvanu GPS poziciju
     * (mapLat/mapLon), generiše je jednom (deterministički, na osnovu
     * uid-a) i upisuje je u Firestore da ostane stabilna pri narednim
     * otvaranjima mape.
     */
    public void loadMapPoints(MapPointsCallback callback) {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<RegionMapPoint> points = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String regionName = doc.getString("region");
                        SerbianRegion region = SerbianRegion.fromDisplayName(regionName);
                        if (region == null) continue; // gost ili region nije postavljen

                        String uid = doc.getId();
                        String username = doc.getString("username");
                        if (username == null) username = "Igrač";

                        Double savedLat = doc.getDouble("mapLat");
                        Double savedLon = doc.getDouble("mapLon");

                        double lat, lon;
                        if (savedLat != null && savedLon != null) {
                            lat = savedLat;
                            lon = savedLon;
                        } else {
                            double[] generated = RegionGeometry.randomPointInside(
                                    region.boundary, RegionGeometry.seedFromUid(uid));
                            lat = generated[0];
                            lon = generated[1];
                            persistMapPoint(uid, lat, lon); // upiši za sledeći put, ne čekamo rezultat
                        }

                        points.add(new RegionMapPoint(uid, username, region, lat, lon));
                    }
                    callback.onSuccess(points);
                })
                .addOnFailureListener(e ->
                        callback.onError("Greška pri učitavanju mape: " + e.getMessage()));
    }

    private void persistMapPoint(String uid, double lat, double lon) {
        Map<String, Object> data = new HashMap<>();
        data.put("mapLat", lat);
        data.put("mapLon", lon);
        db.collection("users").document(uid)
                .set(data, SetOptions.merge());
    }

    /**
     * Učitaj agregirane statistike za svaki region: aktivni/ukupno igrača
     * (d.ii, d.iii), trenutne mesečne zvezde (b), i kumulativni broj
     * 1./2./3. mesta iz arhive (d.i).
     */
    public void loadRegionStats(StatsCallback callback) {
        db.collection("users")
                .get()
                .addOnSuccessListener((QuerySnapshot usersSnapshot) -> {
                    Map<SerbianRegion, int[]> counts = new EnumMap<>(SerbianRegion.class); // [total, active]
                    Map<SerbianRegion, Long> monthlyStars = new EnumMap<>(SerbianRegion.class);
                    for (SerbianRegion r : SerbianRegion.values()) {
                        counts.put(r, new int[]{0, 0});
                        monthlyStars.put(r, 0L);
                    }

                    for (DocumentSnapshot doc : usersSnapshot.getDocuments()) {
                        SerbianRegion region = SerbianRegion.fromDisplayName(doc.getString("region"));
                        if (region == null) continue;

                        int[] c = counts.get(region);
                        c[0]++; // total

                        Boolean online = doc.getBoolean("online");
                        if (Boolean.TRUE.equals(online)) c[1]++;

                        Long stars = doc.getLong("monthlyStars");
                        if (stars == null) stars = 0L;
                        monthlyStars.put(region, monthlyStars.get(region) + stars);
                    }

                    // Sada pročitaj arhivu da saznamo kumulativni broj 1./2./3. mesta po regionu.
                    db.collection("regionCycleHistory")
                            .get()
                            .addOnSuccessListener(historySnapshot -> {
                                Map<SerbianRegion, int[]> podium = new EnumMap<>(SerbianRegion.class); // [1st, 2nd, 3rd]
                                for (SerbianRegion r : SerbianRegion.values()) podium.put(r, new int[]{0, 0, 0});

                                for (DocumentSnapshot doc : historySnapshot.getDocuments()) {
                                    SerbianRegion region = SerbianRegion.fromDisplayName(doc.getString("regionName"));
                                    if (region == null) continue;
                                    Long position = doc.getLong("position");
                                    if (position == null) continue;

                                    int[] p = podium.get(region);
                                    if (position == 1) p[0]++;
                                    else if (position == 2) p[1]++;
                                    else if (position == 3) p[2]++;
                                }

                                Map<SerbianRegion, RegionStats> result = new EnumMap<>(SerbianRegion.class);
                                for (SerbianRegion r : SerbianRegion.values()) {
                                    int[] c = counts.get(r);
                                    int[] p = podium.get(r);
                                    result.put(r, new RegionStats(
                                            r, c[1], c[0], monthlyStars.get(r),
                                            p[0], p[1], p[2]));
                                }
                                callback.onSuccess(result);
                            })
                            .addOnFailureListener(e ->
                                    callback.onError("Greška pri učitavanju istorije regiona: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        callback.onError("Greška pri učitavanju statistike regiona: " + e.getMessage()));
    }

    /**
     * Specifikacija 5.b — rang lista regiona po ukupnom broju zvezda
     * osvojenih u TRENUTNOM mesečnom ciklusu, sortirana opadajuće.
     */
    public void loadMonthlyRegionRanking(MonthlyRankingCallback callback) {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<SerbianRegion, Long> starsByRegion = new EnumMap<>(SerbianRegion.class);
                    for (SerbianRegion r : SerbianRegion.values()) starsByRegion.put(r, 0L);

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SerbianRegion region = SerbianRegion.fromDisplayName(doc.getString("region"));
                        if (region == null) continue;
                        Long stars = doc.getLong("monthlyStars");
                        if (stars == null) stars = 0L;
                        starsByRegion.put(region, starsByRegion.get(region) + stars);
                    }

                    List<RegionMonthlyRankEntry> ranking = new ArrayList<>();
                    for (Map.Entry<SerbianRegion, Long> entry : starsByRegion.entrySet()) {
                        ranking.add(new RegionMonthlyRankEntry(entry.getKey(), entry.getValue()));
                    }
                    Collections.sort(ranking, (a, b) -> Long.compare(b.totalStars, a.totalStars));

                    callback.onSuccess(ranking);
                })
                .addOnFailureListener(e ->
                        callback.onError("Greška pri učitavanju mesečnog ranga regiona: " + e.getMessage()));
    }

    /**
     * Specifikacija 5.e — plasman ZADATOG regiona na POSLEDNJEM
     * (najskorije arhiviranom) mesečnom ciklusu. Vraća {@code null} u
     * callback-u ako region nije bio u top 3, ili arhiva još ne postoji.
     */
    public void loadLastCycleResultForRegion(SerbianRegion region, LastCycleResultCallback callback) {
        if (region == null) {
            callback.onSuccess(null);
            return;
        }

        // Učitaj poslednjih nekoliko zapisa i pronađi tačan, najveći
        // cycleEndMillis — ne pretpostavljamo da poslednji ciklus ima baš
        // 3 zapisa (npr. ako neki region nije osvojio nijednu zvezdu,
        // ciklus može imati i manje od 3 zapisa).
        db.collection("regionCycleHistory")
                .orderBy("cycleEndMillis", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = snapshot.getDocuments();
                    if (docs.isEmpty()) {
                        callback.onSuccess(null); // arhiva još ne postoji
                        return;
                    }

                    Long latestCycleEnd = docs.get(0).getLong("cycleEndMillis");

                    for (DocumentSnapshot doc : docs) {
                        Long cycleEnd = doc.getLong("cycleEndMillis");
                        // Samo zapisi iz NAJSKORIJEG ciklusa — preskoči starije.
                        if (cycleEnd == null || !cycleEnd.equals(latestCycleEnd)) continue;

                        String regionName = doc.getString("regionName");
                        if (region.displayName.equalsIgnoreCase(regionName)) {
                            Long position = doc.getLong("position");
                            Long totalStars = doc.getLong("totalStars");
                            callback.onSuccess(new RegionCycleResult(
                                    regionName,
                                    position != null ? position.intValue() : 0,
                                    totalStars != null ? totalStars.intValue() : 0,
                                    cycleEnd));
                            return;
                        }
                    }
                    callback.onSuccess(null); // region nije bio u top 3 poslednjeg ciklusa
                })
                .addOnFailureListener(e ->
                        callback.onError("Greška pri učitavanju poslednjeg ciklusa: " + e.getMessage()));
    }
}