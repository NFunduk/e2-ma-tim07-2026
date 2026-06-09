package com.example.sabona.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Piše statistiku u Firestore dokument stats/{uid}.
 * Koristi merge (SetOptions.merge) da ne briše postojeće podatke.
 * Za min/max: čita dokument pa piše (read-then-write).
 */
public class StatsRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String uid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    // ── Ko zna zna ──────────────────────────────────────────────────────────
    public void saveKoZnaZnaResult(int myScore, int correctCount, int wrongCount) {
        String uid = uid();
        if (uid == null) return;

        db.collection("stats").document(uid).get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("kzzCorrect", FieldValue.increment(correctCount));
                    data.put("kzzWrong",   FieldValue.increment(wrongCount));

                    long curMin = doc.contains("kzzMinPts") ? toLong(doc.get("kzzMinPts")) : myScore;
                    long curMax = doc.contains("kzzMaxPts") ? toLong(doc.get("kzzMaxPts")) : myScore;
                    data.put("kzzMinPts", Math.min(curMin, myScore));
                    data.put("kzzMaxPts", Math.max(curMax, myScore));

                    db.collection("stats").document(uid).set(data, SetOptions.merge());
                });
    }

    // ── Spojnice ────────────────────────────────────────────────────────────
    public void saveSpojniceResult(int myConnected, int myTotal, int myScore) {
        String uid = uid();
        if (uid == null) return;

        db.collection("stats").document(uid).get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("spojConnected", FieldValue.increment(myConnected));
                    data.put("spojTotal",     FieldValue.increment(myTotal));

                    long curMin = doc.contains("spojMinPts") ? toLong(doc.get("spojMinPts")) : myScore;
                    long curMax = doc.contains("spojMaxPts") ? toLong(doc.get("spojMaxPts")) : myScore;
                    data.put("spojMinPts", Math.min(curMin, myScore));
                    data.put("spojMaxPts", Math.max(curMax, myScore));

                    db.collection("stats").document(uid).set(data, SetOptions.merge());
                });
    }

    // ── Asocijacije ─────────────────────────────────────────────────────────
    public void saveAsocijacijeResult(int myScore, boolean solved) {
        String uid = uid();
        if (uid == null) return;

        db.collection("stats").document(uid).get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put(solved ? "asocSolved" : "asocUnsolved", FieldValue.increment(1));

                    long curMin = doc.contains("asocMinPts") ? toLong(doc.get("asocMinPts")) : myScore;
                    long curMax = doc.contains("asocMaxPts") ? toLong(doc.get("asocMaxPts")) : myScore;
                    data.put("asocMinPts", Math.min(curMin, myScore));
                    data.put("asocMaxPts", Math.max(curMax, myScore));

                    db.collection("stats").document(uid).set(data, SetOptions.merge());
                });
    }

    // ── Skočko ──────────────────────────────────────────────────────────────
    // attemptGroup: 1 = pogodio u pok. 1-2, 2 = pok. 3-4, 3 = pok. 5-6, 0 = nije pogodio
    public void saveSkockoResult(int myScore, int attemptGroup) {
        String uid = uid();
        if (uid == null) return;

        db.collection("stats").document(uid).get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("skockoTotal", FieldValue.increment(1));
                    if (attemptGroup == 1) data.put("skockoAttempts12", FieldValue.increment(1));
                    else if (attemptGroup == 2) data.put("skockoAttempts34", FieldValue.increment(1));
                    else if (attemptGroup == 3) data.put("skockoAttempts56", FieldValue.increment(1));

                    long curMin = doc.contains("skockoMinPts") ? toLong(doc.get("skockoMinPts")) : myScore;
                    long curMax = doc.contains("skockoMaxPts") ? toLong(doc.get("skockoMaxPts")) : myScore;
                    data.put("skockoMinPts", Math.min(curMin, myScore));
                    data.put("skockoMaxPts", Math.max(curMax, myScore));

                    db.collection("stats").document(uid).set(data, SetOptions.merge());
                });
    }

    // ── Korak po korak ──────────────────────────────────────────────────────
    // stepGuessed: 1-7 = u kom koraku pogodio, 0 = nije pogodio
    public void saveKorakResult(int myScore, int stepGuessed) {
        String uid = uid();
        if (uid == null) return;

        db.collection("stats").document(uid).get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("korakTotal", FieldValue.increment(1));
                    if (stepGuessed >= 1 && stepGuessed <= 7) {
                        // Čuvamo kao korakStep0..korakStep6 (flat polja, ne nested)
                        data.put("korakStep" + (stepGuessed - 1), FieldValue.increment(1));
                    }

                    long curMin = doc.contains("korakMinPts") ? toLong(doc.get("korakMinPts")) : myScore;
                    long curMax = doc.contains("korakMaxPts") ? toLong(doc.get("korakMaxPts")) : myScore;
                    data.put("korakMinPts", Math.min(curMin, myScore));
                    data.put("korakMaxPts", Math.max(curMax, myScore));

                    db.collection("stats").document(uid).set(data, SetOptions.merge());
                });
    }

    // ── Moj broj ────────────────────────────────────────────────────────────
    public void saveMojBrojResult(int myScore, boolean foundExact) {
        String uid = uid();
        if (uid == null) return;

        db.collection("stats").document(uid).get()
                .addOnSuccessListener(doc -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("mojBrojTotal", FieldValue.increment(1));
                    if (foundExact) data.put("mojBrojCorrect", FieldValue.increment(1));

                    long curMin = doc.contains("mojBrojMinPts") ? toLong(doc.get("mojBrojMinPts")) : myScore;
                    long curMax = doc.contains("mojBrojMaxPts") ? toLong(doc.get("mojBrojMaxPts")) : myScore;
                    data.put("mojBrojMinPts", Math.min(curMin, myScore));
                    data.put("mojBrojMaxPts", Math.max(curMax, myScore));

                    db.collection("stats").document(uid).set(data, SetOptions.merge());
                });
    }

    // ── Opšta statistika partije ────────────────────────────────────────────
    public void incrementGamesPlayed(boolean won) {
        String uid = uid();
        if (uid == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("totalGames", FieldValue.increment(1));
        data.put(won ? "wins" : "losses", FieldValue.increment(1));

        db.collection("stats").document(uid)
                .set(data, SetOptions.merge());
    }

    // ── Helper ──────────────────────────────────────────────────────────────
    private long toLong(Object v) {
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Double)  return ((Double) v).longValue();
        return 0L;
    }
}