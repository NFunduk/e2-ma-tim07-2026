package com.example.sabona.tournament;

import com.example.sabona.game.GameSessionManager;
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
 * Turnir:
 * - 4 igrača čekaju u redu
 * - ulaz košta 3 tokena
 * - prave se 2 polufinalne partije
 * - pobednici polufinala igraju finale
 */
public class TournamentRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
            : null;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private DocumentReference userRef(String uid) {
        return db.collection("users").document(uid);
    }

    private DocumentReference myQueueRef() {
        return db.collection("tournamentQueue").document(myUid);
    }

    public void joinTournamentQueue(Callback<Void> cb) {
        if (myUid == null) {
            cb.onError("Nisi prijavljen/a.");
            return;
        }

        db.runTransaction(transaction -> {
                    DocumentSnapshot userSnap = transaction.get(userRef(myUid));

                    long tokens = userSnap.getLong("tokens") != null
                            ? userSnap.getLong("tokens")
                            : 0;

                    if (tokens < 3) {
                        throw new RuntimeException("NO_TOKENS");
                    }

                    String username = userSnap.getString("username");
                    String avatarRes = userSnap.getString("avatarRes");
                    Long league = userSnap.getLong("league");

                    transaction.update(userRef(myUid), "tokens", FieldValue.increment(-3));

                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", myUid);
                    data.put("username", username != null ? username : "Igrač");
                    data.put("avatarRes", avatarRes);
                    data.put("league", league != null ? league : 0);
                    data.put("status", "waiting");
                    data.put("tournamentId", null);
                    data.put("sessionId", null);
                    data.put("createdAt", FieldValue.serverTimestamp());

                    transaction.set(myQueueRef(), data);

                    return null;
                }).addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("NO_TOKENS")) {
                        cb.onError("Nemaš dovoljno tokena. Turnir košta 3 tokena.");
                    } else {
                        cb.onError("Greška: " + e.getMessage());
                    }
                });
    }

    public void cancelQueue() {
        if (myUid == null) return;

        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(myQueueRef());

            if (snap.exists() && "waiting".equals(snap.getString("status"))) {
                //transaction.update(userRef(myUid), "tokens", FieldValue.increment(3));
                transaction.delete(myQueueRef());
            }

            return null;
        });
    }

    public void tryCreateTournament(Callback<String> cb) {
        db.collection("tournamentQueue")
                .whereEqualTo("status", "waiting")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(4)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.size() < 4) {
                        cb.onSuccess(null);
                        return;
                    }

                    List<DocumentSnapshot> players = snap.getDocuments();
                    createTournamentFromPlayers(players, cb);
                })
                .addOnFailureListener(e -> cb.onError("Greška: " + e.getMessage()));
    }

    private void createTournamentFromPlayers(List<DocumentSnapshot> players, Callback<String> cb) {
        String tournamentId = "T" + System.currentTimeMillis();
        String semi1SessionId = tournamentId + "_S1";
        String semi2SessionId = tournamentId + "_S2";

        db.runTransaction(transaction -> {
                    List<Map<String, Object>> playerInfos = new ArrayList<>();

                    for (DocumentSnapshot p : players) {
                        DocumentSnapshot fresh = transaction.get(p.getReference());

                        if (!fresh.exists() || !"waiting".equals(fresh.getString("status"))) {
                            throw new RuntimeException("TAKEN");
                        }

                        Map<String, Object> player = new HashMap<>();
                        player.put("uid", fresh.getString("uid"));
                        player.put("username", fresh.getString("username"));
                        player.put("avatarRes", fresh.getString("avatarRes"));
                        player.put("league", fresh.getLong("league") != null ? fresh.getLong("league") : 0);

                        playerInfos.add(player);
                    }

                    java.util.Collections.shuffle(playerInfos);

                    List<String> uids = new ArrayList<>();
                    List<String> usernames = new ArrayList<>();

                    for (Map<String, Object> p : playerInfos) {
                        uids.add((String) p.get("uid"));
                        usernames.add((String) p.get("username"));
                    }

                    Map<String, Object> tournament = new HashMap<>();
                    tournament.put("status", "semifinal");
                    tournament.put("players", uids);
                    tournament.put("usernames", usernames);
                    tournament.put("playerInfos", playerInfos);
                    tournament.put("semi1SessionId", semi1SessionId);
                    tournament.put("semi2SessionId", semi2SessionId);
                    tournament.put("finalSessionId", null);
                    tournament.put("semi1WinnerUid", null);
                    tournament.put("semi2WinnerUid", null);
                    tournament.put("winnerUid", null);
                    tournament.put("createdAt", FieldValue.serverTimestamp());

                    transaction.set(db.collection("tournaments").document(tournamentId), tournament);

                    createGameSession(transaction, semi1SessionId, tournamentId, "semifinal", 1, uids.get(0), uids.get(1));
                    createGameSession(transaction, semi2SessionId, tournamentId, "semifinal", 2, uids.get(2), uids.get(3));

                    for (int i = 0; i < 4; i++) {
                        String sessionId = i < 2 ? semi1SessionId : semi2SessionId;

                        transaction.update(
                                db.collection("tournamentQueue").document(uids.get(i)),
                                "status", "matched",
                                "tournamentId", tournamentId,
                                "sessionId", sessionId
                        );
                    }

                    return tournamentId;
                }).addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(e -> cb.onError("Greška pri kreiranju turnira: " + e.getMessage()));
    }

    private void createGameSession(com.google.firebase.firestore.Transaction transaction,
                                   String sessionId,
                                   String tournamentId,
                                   String round,
                                   int bracketIndex,
                                   String p1,
                                   String p2) {

        Map<String, Object> session = new HashMap<>();
        session.put("status", "active");
        session.put("isFriendlyMatch", false);
        session.put("isTournamentMatch", true);
        session.put("tournamentId", tournamentId);
        session.put("tournamentRound", round);
        session.put("bracketIndex", bracketIndex);
        session.put("player1Uid", p1);
        session.put("player2Uid", p2);
        session.put("totalScoreP1", 0);
        session.put("totalScoreP2", 0);
        session.put("leftByUid", null);
        session.put("createdAt", FieldValue.serverTimestamp());

        transaction.set(db.collection(GameSessionManager.COL_GAME_SESSIONS).document(sessionId), session);
    }

    public void finishTournamentMatch(String sessionId, Callback<String> cb) {
        if (sessionId == null || sessionId.isEmpty()) {
            cb.onError("Nema sessionId");
            return;
        }

        DocumentReference sessionRef = db.collection(GameSessionManager.COL_GAME_SESSIONS).document(sessionId);

        db.runTransaction(transaction -> {
                    DocumentSnapshot sessionSnap = transaction.get(sessionRef);

                    if (!sessionSnap.exists()) return null;

                    Boolean isTournament = sessionSnap.getBoolean("isTournamentMatch");
                    if (!Boolean.TRUE.equals(isTournament)) return null;

                    String alreadyWinner = sessionSnap.getString("winnerUid");
                    if (alreadyWinner != null) return null;

                    String tournamentId = sessionSnap.getString("tournamentId");
                    String round = sessionSnap.getString("tournamentRound");
                    Long bracketLong = sessionSnap.getLong("bracketIndex");

                    String p1 = sessionSnap.getString("player1Uid");
                    String p2 = sessionSnap.getString("player2Uid");

                    long s1 = sessionSnap.getLong("totalScoreP1") != null ? sessionSnap.getLong("totalScoreP1") : 0;
                    long s2 = sessionSnap.getLong("totalScoreP2") != null ? sessionSnap.getLong("totalScoreP2") : 0;

                    String winnerUid = s1 >= s2 ? p1 : p2;
                    String loserUid = winnerUid.equals(p1) ? p2 : p1;

                    transaction.update(sessionRef,
                            "status", "finished",
                            "winnerUid", winnerUid,
                            "finishedAt", FieldValue.serverTimestamp()
                    );

                    DocumentReference tournamentRef = db.collection("tournaments").document(tournamentId);
                    DocumentSnapshot tournamentSnap = transaction.get(tournamentRef);

                    if ("semifinal".equals(round)) {
                        int bracket = bracketLong != null ? bracketLong.intValue() : 0;

                        String semi1Winner = tournamentSnap.getString("semi1WinnerUid");
                        String semi2Winner = tournamentSnap.getString("semi2WinnerUid");

                        if (bracket == 1) semi1Winner = winnerUid;
                        if (bracket == 2) semi2Winner = winnerUid;

                        Map<String, Object> updates = new HashMap<>();
                        if (bracket == 1) updates.put("semi1WinnerUid", winnerUid);
                        if (bracket == 2) updates.put("semi2WinnerUid", winnerUid);

                        transaction.update(userRef(winnerUid), "tokens", FieldValue.increment(2));

                        transaction.update(db.collection("tournamentQueue").document(winnerUid),
                                "status", "waiting_final");

                        transaction.update(db.collection("tournamentQueue").document(loserUid),
                                "status", "eliminated");

                        if (semi1Winner != null && semi2Winner != null) {
                            String finalSessionId = tournamentId + "_F";

                            updates.put("status", "final");
                            updates.put("finalSessionId", finalSessionId);

                            createGameSession(transaction, finalSessionId, tournamentId,
                                    "final", 0, semi1Winner, semi2Winner);

                            transaction.update(db.collection("tournamentQueue").document(semi1Winner),
                                    "status", "matched",
                                    "sessionId", finalSessionId);

                            transaction.update(db.collection("tournamentQueue").document(semi2Winner),
                                    "status", "matched",
                                    "sessionId", finalSessionId);

                            transaction.update(tournamentRef, updates);
                            return finalSessionId;
                        }

                        transaction.update(tournamentRef, updates);
                        return null;
                    }

                    if ("final".equals(round)) {
                        transaction.update(tournamentRef,
                                "status", "finished",
                                "winnerUid", winnerUid,
                                "finishedAt", FieldValue.serverTimestamp()
                        );

                        transaction.update(userRef(winnerUid),
                                "tokens", FieldValue.increment(3),
                                "stars", FieldValue.increment(10),
                                "weeklyStars", FieldValue.increment(10),
                                "monthlyStars", FieldValue.increment(10)
                        );

                        transaction.update(db.collection("tournamentQueue").document(winnerUid),
                                "status", "winner");

                        transaction.update(db.collection("tournamentQueue").document(loserUid),
                                "status", "final_loser");

                        return null;
                    }

                    return null;
                }).addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public ListenerRegistration listenMyTournamentMatch(Callback<String> cb) {
        return myQueueRef().addSnapshotListener((snap, e) -> {
            if (snap == null || !snap.exists()) return;

            if ("matched".equals(snap.getString("status"))) {
                String sessionId = snap.getString("sessionId");
                if (sessionId != null) cb.onSuccess(sessionId);
            }
        });
    }

    public static class TournamentPlayer {
        public String uid;
        public String username;
        public String avatarRes;
        public long league;

        public TournamentPlayer(String uid, String username, String avatarRes, long league) {
            this.uid = uid;
            this.username = username;
            this.avatarRes = avatarRes;
            this.league = league;
        }
    }

    public ListenerRegistration listenWaitingPlayers(Callback<List<TournamentPlayer>> cb) {
        return db.collection("tournamentQueue")
                .whereEqualTo("status", "waiting")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(4)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        cb.onError(e.getMessage());
                        return;
                    }

                    List<TournamentPlayer> list = new ArrayList<>();

                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            String uid = doc.getString("uid");
                            String username = doc.getString("username");
                            String avatarRes = doc.getString("avatarRes");
                            Long league = doc.getLong("league");

                            list.add(new TournamentPlayer(
                                    uid,
                                    username != null ? username : "Igrač",
                                    avatarRes,
                                    league != null ? league : 0
                            ));
                        }
                    }

                    cb.onSuccess(list);
                });
    }
}