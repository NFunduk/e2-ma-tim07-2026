package com.example.sabona.challenge;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallengeRepository {

    private static final String COL = "challenges";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback       { void onSuccess(); void onError(String msg); }
    public interface IdCallback     { void onSuccess(String id); void onError(String msg); }

    public interface ListCallback {
        void onSuccess(List<Challenge> list);
        void onError(String msg);
        void onRetry();
    }

    /**
     * Kreira novi izazov i vraća njegov Firestore ID direktno kroz callback.
     * Koristiti umesto createChallenge() + observe() kad je potreban ID odmah.
     */
    public void createChallengeAndGetId(Challenge challenge, IdCallback cb) {
        db.collection(COL)
                .add(challengeToMap(challenge))
                .addOnSuccessListener(ref -> {
                    String newId = ref.getId();
                    deductWager(challenge.getCreatorUid(),
                            challenge.getStarsWager(), challenge.getTokensWager(),
                            new Callback() {
                                @Override public void onSuccess() { cb.onSuccess(newId); }
                                @Override public void onError(String msg) { cb.onError(msg); }
                            });
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Kreira novi izazov u Firestore-u i oduzima ulog kreatoreu. */
    public void createChallenge(Challenge challenge, Callback cb) {
        db.collection(COL)
                .add(challengeToMap(challenge))
                .addOnSuccessListener(ref -> {
                    // Oduzmi uloge kreatoreu (zvezde + tokeni)
                    deductWager(challenge.getCreatorUid(),
                            challenge.getStarsWager(), challenge.getTokensWager(),
                            cb);
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Igrač prihvata izazov — dodaje se u listu učesnika i plaća ulog. */
    public void joinChallenge(String challengeId, String uid, String username,
                              int starsWager, int tokensWager, Callback cb) {
        Map<String, Object> update = new HashMap<>();
        update.put("participantUids",      FieldValue.arrayUnion(uid));
        update.put("participantUsernames", FieldValue.arrayUnion(username));
        update.put("participantScores",    FieldValue.arrayUnion(0));

        db.collection(COL).document(challengeId)
                .update(update)
                .addOnSuccessListener(v -> deductWager(uid, starsWager, tokensWager, cb))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Upisuje finalni skor igrača u challenge dokument. */
    public void submitScore(String challengeId, String uid, int score, Callback cb) {
        // Čitamo dokument da nađemo index igrača, pa atomski ažuriramo
        db.collection(COL).document(challengeId).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) { cb.onError("Izazov nije nađen"); return; }
                    Challenge ch = snap.toObject(Challenge.class);
                    if (ch == null) { cb.onError("Greška deserijalizacije"); return; }

                    List<String> uids = ch.getParticipantUids();
                    int idx = uids.indexOf(uid);
                    if (idx < 0) { cb.onError("Nisi učesnik ovog izazova"); return; }

                    // Firestore ne podržava direktno update elementa niza po indeksu
                    // bez transakcije — koristimo mapu sa dot-notacijom
                    Map<String, Object> update = new HashMap<>();
                    update.put("participantScores." + idx, score);

                    // Ako su svi igrači predali skor, finalizujemo izazov
                    List<Integer> scores = ch.getParticipantScores();
                    int submittedCount = 0;
                    for (int s : scores) if (s > 0) submittedCount++;
                    // +1 jer ovaj igrač upravo predaje
                    if (submittedCount + 1 >= uids.size()) {
                        update.put("status", Challenge.STATUS_FINISHED);
                    }

                    db.collection(COL).document(challengeId)
                            .update(update)
                            .addOnSuccessListener(v -> {
                                // Ako je gotov, raspodeli nagrade
                                if (Challenge.STATUS_FINISHED.equals(update.get("status"))) {
                                    distributeRewards(challengeId, ch, scores, idx, score);
                                }
                                cb.onSuccess();
                            })
                            .addOnFailureListener(e -> cb.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /**
     * Raspodela nagrade po specifikaciji 9.d:
     * - 1. mesto (najviše bodova): dobija 75% ukupnog uloga
     * - 2. mesto: dobija nazad uloženo
     * - ostali: gube ulog
     */
    private void distributeRewards(String challengeId, Challenge ch,
                                   List<Integer> oldScores, int updatedIdx, int newScore) {
        // Rekonstruišemo finalne skorove sa novim vrednostima
        List<Integer> finalScores = new java.util.ArrayList<>(oldScores);
        finalScores.set(updatedIdx, newScore);

        List<String> uids = ch.getParticipantUids();
        int n = uids.size();
        int totalStars  = ch.getStarsWager()  * n;
        int totalTokens = ch.getTokensWager() * n;

        // Sortiraj po skoru (desc) da nađemo mesta
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> finalScores.get(b) - finalScores.get(a));

        for (int rank = 0; rank < order.length; rank++) {
            int playerIdx = order[rank];
            String uid = uids.get(playerIdx);

            Map<String, Object> delta = new HashMap<>();
            if (rank == 0) {
                // 1. mesto: 75% ukupnog
                delta.put("stars",  FieldValue.increment((long) (totalStars * 0.75)));
                delta.put("tokens", FieldValue.increment((long) (totalTokens * 0.75)));
            } else if (rank == 1) {
                // 2. mesto: dobija nazad uloženo (neto 0)
                delta.put("stars",  FieldValue.increment(ch.getStarsWager()));
                delta.put("tokens", FieldValue.increment(ch.getTokensWager()));
            }
            // ostali: ne dobijaju ništa (ulog je već oduzet pri ulasku)

            if (!delta.isEmpty()) {
                db.collection("users").document(uid).update(delta);
            }
        }
    }

    /** Sluša u realnom vremenu izazove za dati region (samo OPEN i FINISHED od nedavno). */
    public ListenerRegistration listenChallenges(String region, ListCallback cb) {
        return db.collection(COL)
                .whereEqualTo("region", region)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener((snaps, e) -> {
                    if (e != null) {
                        // Ako index još nije gotov, pokušaj ponovo za 5 sekundi
                        if (e.getMessage() != null && e.getMessage().contains("index")) {
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(() -> cb.onRetry(), 5000);
                        } else {
                            cb.onError(e.getMessage());
                        }
                        return;
                    }
                    if (snaps == null) return;
                    List<Challenge> list = snaps.toObjects(Challenge.class);
                    for (int i = 0; i < list.size(); i++) {
                        list.get(i).setId(snaps.getDocuments().get(i).getId());
                    }
                    cb.onSuccess(list);
                });
    }

    // --- helpers ---

    private void deductWager(String uid, int stars, int tokens, Callback cb) {
        Map<String, Object> delta = new HashMap<>();
        delta.put("stars",  FieldValue.increment(-stars));
        delta.put("tokens", FieldValue.increment(-tokens));
        db.collection("users").document(uid)
                .update(delta)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    private Map<String, Object> challengeToMap(Challenge ch) {
        Map<String, Object> m = new HashMap<>();
        m.put("creatorUid",            ch.getCreatorUid());
        m.put("creatorUsername",       ch.getCreatorUsername());
        m.put("region",                ch.getRegion());
        m.put("starsWager",            ch.getStarsWager());
        m.put("tokensWager",           ch.getTokensWager());
        m.put("status",                ch.getStatus());
        m.put("participantUids",       ch.getParticipantUids());
        m.put("participantUsernames",  ch.getParticipantUsernames());
        m.put("participantScores",     ch.getParticipantScores());
        m.put("createdAt",             ch.getCreatedAt());
        return m;
    }
}