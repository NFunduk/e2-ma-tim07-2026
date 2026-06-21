package com.example.sabona.region;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Servis koji agregira trenutni mesečni broj zvezda po regionu
 * (specifikacija 5.b) i — kad se ciklus zaključuje — arhivira top 3
 * regiona u Firestore kolekciju "regionCycleHistory" (potrebno za 5.d.i
 * i 5.e, vidi {@link RegionCycleResult}).
 *
 * VAŽNO: {@link #snapshotAndArchive(SnapshotCallback)} MORA biti pozvan
 * PRE {@code LeaderboardRepository.resetMonthlyCycle()}, jer taj poziv
 * resetuje "monthlyStars" na 0 — nakon toga, podatak ko je bio prvi se
 * gubi. Ovo je jedina tačka dodira sa logikom rang liste (tačka 4 iz
 * specifikacije, koju radi koleginica); ne menja se ništa u njenom kodu,
 * samo se njeni podaci ČITAJU pre nego što ih ona resetuje.
 */
public class RegionCycleService {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface SnapshotCallback {
        void onSuccess(List<RegionCycleResult> top3);
        void onError(String message);
    }

    /**
     * Učitaj sve korisnike, saberi "monthlyStars" po regionu, rangiraj
     * regione po ukupnim zvezdama, i upiši top 3 u "regionCycleHistory".
     * Ne diraj ništa drugo (ne resetuje zvezde — to ostaje posao
     * {@code LeaderboardRepository.resetMonthlyCycle()}, koji se zove
     * POSLE ovoga).
     */
    public void snapshotAndArchive(SnapshotCallback callback) {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<SerbianRegion, Long> starsByRegion = new EnumMap<>(SerbianRegion.class);
                    for (SerbianRegion r : SerbianRegion.values()) starsByRegion.put(r, 0L);

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        SerbianRegion region = SerbianRegion.fromDisplayName(doc.getString("region"));
                        if (region == null) continue;

                        Long monthlyStars = doc.getLong("monthlyStars");
                        if (monthlyStars == null) monthlyStars = 0L;

                        starsByRegion.put(region, starsByRegion.get(region) + monthlyStars);
                    }

                    List<Map.Entry<SerbianRegion, Long>> sorted = new ArrayList<>(starsByRegion.entrySet());
                    sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

                    long now = System.currentTimeMillis();
                    List<RegionCycleResult> top3 = new ArrayList<>();
                    com.google.firebase.firestore.WriteBatch batch = db.batch();

                    int max = Math.min(3, sorted.size());
                    for (int i = 0; i < max; i++) {
                        Map.Entry<SerbianRegion, Long> entry = sorted.get(i);
                        if (entry.getValue() <= 0) continue; // region bez ijedne zvezde ne osvaja mesto

                        int position = i + 1;
                        RegionCycleResult result = new RegionCycleResult(
                                entry.getKey().displayName, position, entry.getValue().intValue(), now);
                        top3.add(result);

                        String docId = now + "_" + entry.getKey().name();
                        Map<String, Object> data = new java.util.HashMap<>();
                        data.put("regionName", result.regionName);
                        data.put("position", result.position);
                        data.put("totalStars", result.totalStars);
                        data.put("cycleEndMillis", result.cycleEndMillis);

                        batch.set(db.collection("regionCycleHistory").document(docId), data);
                    }

                    batch.commit()
                            .addOnSuccessListener(v -> callback.onSuccess(top3))
                            .addOnFailureListener(e ->
                                    callback.onError("Greška pri arhiviranju ciklusa regiona: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        callback.onError("Greška pri učitavanju podataka za arhiviranje: " + e.getMessage()));
    }
}