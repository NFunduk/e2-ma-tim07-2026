package com.example.sabona.matchmaking;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Random matchmaking — bez Cloud Functions, čisto klijentski preko Firestore transakcija.
 *
 * Kolekcija "matchmakingQueue/{uid}":
 *   { uid, status: "waiting"|"matched", sessionId, createdAt }
 *
 * Token se rezerviše (oduzima) ODMAH pri ulasku u red čekanja (ne pri pronalasku
 * protivnika), a vraća se ako igrač otkaže traženje. Ovo izbjegava potrebu za
 * složenom dvostrukom transakcijom nad oba korisnika u trenutku sparivanja.
 */
public class MatchmakingRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private DocumentReference myQueueRef() {
        return db.collection("matchmakingQueue").document(myUid);
    }

    private DocumentReference userRef(String uid) {
        return db.collection("users").document(uid);
    }

    public void joinQueue(Callback<Void> callback) {
        if (myUid == null) { callback.onError("Nisi prijavljen/a"); return; }

        db.runTransaction(transaction -> {
                    DocumentSnapshot userSnap = transaction.get(userRef(myUid));
                    long tokens = userSnap.contains("tokens") && userSnap.getLong("tokens") != null
                            ? userSnap.getLong("tokens") : 0;

                    if (tokens < 1) {
                        throw new RuntimeException("NO_TOKENS");
                    }

                    transaction.update(userRef(myUid), "tokens", FieldValue.increment(-1));

                    Map<String, Object> queueData = new HashMap<>();
                    queueData.put("uid", myUid);
                    queueData.put("status", "waiting");
                    queueData.put("sessionId", null);
                    queueData.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(myQueueRef(), queueData);
                    return null;
                }).addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("NO_TOKENS")) {
                        callback.onError("Nemaš dovoljno tokena za partiju.");
                    } else {
                        callback.onError("Greška: " + e.getMessage());
                    }
                });
    }

    /** Otkaži čekanje — vrati rezervisani token i obriši red. */
    public void cancelQueue() {
        if (myUid == null) return;
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(myQueueRef());
            if (snap.exists() && "waiting".equals(snap.getString("status"))) {
                transaction.update(userRef(myUid), "tokens", FieldValue.increment(1));
            }
            transaction.delete(myQueueRef());
            return null;
        });
    }

    /**
     * Potraži nekoga ko čeka i pokušaj da ga "claim"-uješ transakcijom.
     * Ako je u međuvremenu neko drugi to uradio prvi, transakcija puca
     * (Firestore retry mehanizam) i ide se na sledećeg kandidata.
     */
    public void findOpponentAndMatch(Callback<String[]> callback) {
        db.collection("matchmakingQueue")
                .whereEqualTo("status", "waiting")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(snap -> {
                    List<DocumentSnapshot> candidates = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        if (myUid != null && !myUid.equals(doc.getId())) candidates.add(doc);
                    }
                    tryClaimNext(candidates, 0, callback);
                })
                .addOnFailureListener(e -> callback.onError("Greška pretrage: " + e.getMessage()));
    }

    private void tryClaimNext(List<DocumentSnapshot> candidates, int index, Callback<String[]> callback) {
        if (index >= candidates.size()) {
            callback.onSuccess(null); // niko slobodan trenutno, čekamo preko listenera
            return;
        }

        String opponentUid = candidates.get(index).getId();
        DocumentReference opponentRef = db.collection("matchmakingQueue").document(opponentUid);
        String newSessionId = "M" + (System.currentTimeMillis() % 1000000) + (int) (Math.random() * 90 + 10);

        db.runTransaction(transaction -> {
                    DocumentSnapshot oppSnap = transaction.get(opponentRef);
                    DocumentSnapshot meSnap  = transaction.get(myQueueRef());

                    if (!oppSnap.exists() || !"waiting".equals(oppSnap.getString("status"))) {
                        throw new RuntimeException("TAKEN");
                    }
                    if (!meSnap.exists() || !"waiting".equals(meSnap.getString("status"))) {
                        throw new RuntimeException("SELF_GONE");
                    }

                    transaction.update(opponentRef, "status", "matched", "sessionId", newSessionId);
                    transaction.update(myQueueRef(), "status", "matched", "sessionId", newSessionId);

                    Map<String, Object> sessionData = new HashMap<>();
                    sessionData.put("status", "active");
                    sessionData.put("isFriendlyMatch", false);
                    sessionData.put("player1Uid", myUid);
                    sessionData.put("player2Uid", opponentUid);
                    sessionData.put("totalScoreP1", 0);
                    sessionData.put("totalScoreP2", 0);
                    sessionData.put("leftByUid", null);
                    sessionData.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(db.collection("gameSessions").document(newSessionId), sessionData);

                    return new String[]{newSessionId, myUid, opponentUid};
                }).addOnSuccessListener(result -> callback.onSuccess((String[]) result))
                .addOnFailureListener(e -> tryClaimNext(candidates, index + 1, callback));
    }

    /** Sluša MOJ red — ako me NEKO DRUGI upari, status mog dokumenta postaje "matched". */
    public ListenerRegistration listenForMatch(Callback<String> sessionIdCallback) {
        return myQueueRef().addSnapshotListener((snap, e) -> {
            if (e != null || snap == null || !snap.exists()) return;
            if ("matched".equals(snap.getString("status"))) {
                String sessionId = snap.getString("sessionId");
                if (sessionId != null) sessionIdCallback.onSuccess(sessionId);
            }
        });
    }
}
