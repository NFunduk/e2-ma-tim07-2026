package com.example.sabona.region;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sve Firestore operacije vezane za prikaz regiona (zadatak "5. Prikaz
 * regiona" iz specifikacije).
 *
 * Namerno NE dodiruje ništa vezano za rang liste (taj dio specifikacije,
 * 5b i 5e, zavisi od mesečne rang liste po regionima koja se radi
 * naknadno – vidi napomenu u {@link RegionStats}).
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
     * Učitaj agregirane statistike za svaki region: broj trenutno
     * aktivnih (online) igrača i broj ukupno registrovanih igrača.
     */
    public void loadRegionStats(StatsCallback callback) {
        db.collection("users")
                .get()
                .addOnSuccessListener((QuerySnapshot snapshot) -> {
                    Map<SerbianRegion, int[]> counts = new EnumMap<>(SerbianRegion.class);
                    for (SerbianRegion r : SerbianRegion.values()) {
                        counts.put(r, new int[]{0, 0}); // [total, active]
                    }

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SerbianRegion region = SerbianRegion.fromDisplayName(doc.getString("region"));
                        if (region == null) continue;

                        int[] c = counts.get(region);
                        c[0]++; // total

                        Boolean online = doc.getBoolean("online");
                        if (Boolean.TRUE.equals(online)) c[1]++;
                    }

                    Map<SerbianRegion, RegionStats> result = new EnumMap<>(SerbianRegion.class);
                    for (SerbianRegion r : SerbianRegion.values()) {
                        int[] c = counts.get(r);
                        result.put(r, new RegionStats(r, c[1], c[0]));
                    }
                    callback.onSuccess(result);
                })
                .addOnFailureListener(e ->
                        callback.onError("Greška pri učitavanju statistike regiona: " + e.getMessage()));
    }
}