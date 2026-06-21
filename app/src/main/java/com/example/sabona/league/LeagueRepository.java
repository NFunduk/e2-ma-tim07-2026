package com.example.sabona.league;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Sve Firestore operacije vezane za zvezde i ligu korisnika.
 *
 * Koristi transakcije (atomično čitanje + pisanje) kako bi se
 * izbjeglo "trkaće stanje" pri paralelnim promjenama zvezda.
 */
public class LeagueRepository {

    private final FirebaseFirestore db   = FirebaseFirestore.getInstance();
    private final FirebaseAuth      auth = FirebaseAuth.getInstance();

    //  Callback interfejsi                                                 //

    public interface LeagueCallback {
        void onSuccess(LeagueManager.LeagueChangeResult result);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    //  Primjena promjene zvezda (transakcijski)                            //

    /**
     * Atomično primijeni promjenu zvezda za trenutno ulogovanog igrača.
     * Automatski ažurira i polje `league` ako se liga promijenila.
     *
     * @param starsDelta pozitivan ili negativan broj zvezda
     * @param callback   vraća {@link LeagueManager.LeagueChangeResult}
     */
    public void applyStarChange(int starsDelta, LeagueCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Korisnik nije prijavljen");
            return;
        }

        DocumentReference userRef = db.collection("users").document(user.getUid());

        db.runTransaction((Transaction.Function<LeagueManager.LeagueChangeResult>) transaction -> {
                    // 1. Čitaj trenutne vrijednosti
                    com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(userRef);
                    int currentStars  = snap.contains("stars")  ? snap.getLong("stars").intValue()  : 0;
                    int currentLeague = snap.contains("league") ? snap.getLong("league").intValue() : 0;
                    League old = League.fromIndex(currentLeague);

                    // 2. Izračunaj novo stanje
                    LeagueManager.LeagueChangeResult result =
                            LeagueManager.applyStarChange(currentStars, old, starsDelta);

                    // 3. Upiši novo stanje
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("stars",  result.newStars);
                    updates.put("league", result.newLeague.index);
                    transaction.update(userRef, updates);

                    return result;
                }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError("Greška: " + e.getMessage()));
    }

    /**
     * Primijeni mesečnu kaznu (30% zvezda) za trenutnog igrača.
     */
    public void applyMonthlyPenalty(LeagueCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Korisnik nije prijavljen");
            return;
        }

        DocumentReference userRef = db.collection("users").document(user.getUid());

        db.runTransaction((Transaction.Function<LeagueManager.LeagueChangeResult>) transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(userRef);
                    int currentStars  = snap.contains("stars")  ? snap.getLong("stars").intValue()  : 0;
                    int currentLeague = snap.contains("league") ? snap.getLong("league").intValue() : 0;
                    League old = League.fromIndex(currentLeague);

                    LeagueManager.LeagueChangeResult result =
                            LeagueManager.applyMonthlyPenalty(currentStars, old);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("stars",  result.newStars);
                    updates.put("league", result.newLeague.index);
                    transaction.update(userRef, updates);

                    return result;
                }).addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(e -> callback.onError("Greška: " + e.getMessage()));
    }

    /**
     * Dodaj dnevne tokene za trenutnog igrača.
     * Poziva se iz WorkManager joba koji se okida jednom dnevno.
     *
     * @param league  trenutna liga igrača (za izračun bonusa)
     */
    public void grantDailyTokens(League league, SimpleCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Korisnik nije prijavljen");
            return;
        }

        int tokensToAdd = LeagueManager.dailyTokens(league);
        DocumentReference userRef = db.collection("users").document(user.getUid());

        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(userRef);
                    int currentTokens = snap.contains("tokens") ? snap.getLong("tokens").intValue() : 0;
                    transaction.update(userRef, "tokens", currentTokens + tokensToAdd);
                    return null;
                }).addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Greška pri dodavanju tokena: " + e.getMessage()));
    }

    /**
     * Čitaj trenutne zvezde i ligu za prikaz na ekranu.
     */
    public void loadLeagueData(LeagueDataCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Korisnik nije prijavljen");
            return;
        }

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    int stars  = snap.contains("stars")  ? snap.getLong("stars").intValue()  : 0;
                    int league = snap.contains("league") ? snap.getLong("league").intValue() : 0;
                    callback.onSuccess(stars, League.fromIndex(league));
                })
                .addOnFailureListener(e -> callback.onError("Greška: " + e.getMessage()));
    }

    public interface LeagueDataCallback {
        void onSuccess(int stars, League league);
        void onError(String message);
    }
}