package com.example.sabona.challenge;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallengeRepository {

    private static final String COL = "challenges";
    private static final int MAX_PLAYERS = 4;
    private static final int MAX_STARS_WAGER = 10;
    private static final int MAX_TOKENS_WAGER = 2;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback { void onSuccess(); void onError(String msg); }
    public interface IdCallback { void onSuccess(String id); void onError(String msg); }
    public interface ChallengeCallback { void onSuccess(Challenge ch); void onError(String msg); }

    public interface ListCallback {
        void onSuccess(List<Challenge> list);
        void onError(String msg);
        void onRetry();
    }

    public void createChallengeAndGetId(Challenge challenge, IdCallback cb) {
        if (!isValidWager(challenge.getStarsWager(), challenge.getTokensWager())) {
            cb.onError("Ulog moze biti najvise 10 zvezda i 2 tokena.");
            return;
        }

        DocumentReference challengeRef = db.collection(COL).document();
        DocumentReference userRef = db.collection("users").document(challenge.getCreatorUid());

        db.runTransaction(transaction -> {
                    DocumentSnapshot userSnap = transaction.get(userRef);
                    ensureEnoughBalance(userSnap, challenge.getStarsWager(), challenge.getTokensWager());

                    transaction.set(challengeRef, challengeToMap(challenge));
                    transaction.update(userRef,
                            "stars", FieldValue.increment(-challenge.getStarsWager()),
                            "tokens", FieldValue.increment(-challenge.getTokensWager()));
                    return challengeRef.getId();
                })
                .addOnSuccessListener(cb::onSuccess)
                .addOnFailureListener(e -> cb.onError(cleanError(e)));
    }

    public void createChallenge(Challenge challenge, Callback cb) {
        createChallengeAndGetId(challenge, new IdCallback() {
            @Override public void onSuccess(String id) { cb.onSuccess(); }
            @Override public void onError(String msg) { cb.onError(msg); }
        });
    }

    public void joinChallenge(String challengeId, String uid, String username,
                              int starsWager, int tokensWager, Callback cb) {
        DocumentReference challengeRef = db.collection(COL).document(challengeId);
        DocumentReference userRef = db.collection("users").document(uid);

        db.runTransaction(transaction -> {
                    DocumentSnapshot challengeSnap = transaction.get(challengeRef);
                    Challenge ch = buildChallengeFromSnapshot(challengeSnap);
                    if (ch == null) throw new IllegalStateException("Izazov nije pronadjen");
                    if (Challenge.STATUS_FINISHED.equals(ch.getStatus())) {
                        throw new IllegalStateException("Izazov je vec zavrsen");
                    }
                    if (ch.isFull()) throw new IllegalStateException("Izazov je popunjen");
                    if (ch.getParticipantUids().contains(uid)) {
                        throw new IllegalStateException("Vec si u ovom izazovu");
                    }
                    if (ch.getStarsWager() != starsWager || ch.getTokensWager() != tokensWager) {
                        throw new IllegalStateException("Ulog izazova je promenjen");
                    }

                    DocumentSnapshot userSnap = transaction.get(userRef);
                    ensureEnoughBalance(userSnap, starsWager, tokensWager);

                    List<String> uids = new ArrayList<>(ch.getParticipantUids());
                    List<String> names = new ArrayList<>(ch.getParticipantUsernames());
                    List<Integer> scores = new ArrayList<>(ch.getParticipantScores());

                    uids.add(uid);
                    names.add(username != null && !username.trim().isEmpty() ? username : "Igrac");
                    scores.add(0);

                    Map<String, Object> update = new HashMap<>();
                    update.put("participantUids", uids);
                    update.put("participantUsernames", names);
                    update.put("participantScores", scores);
                    if (uids.size() >= MAX_PLAYERS) {
                        update.put("status", Challenge.STATUS_IN_PROGRESS);
                    }

                    transaction.update(challengeRef, update);
                    transaction.update(userRef,
                            "stars", FieldValue.increment(-starsWager),
                            "tokens", FieldValue.increment(-tokensWager));
                    return null;
                })
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(cleanError(e)));
    }

    public void loadChallenge(String challengeId, ChallengeCallback cb) {
        db.collection(COL).document(challengeId).get()
                .addOnSuccessListener(snap -> {
                    Challenge ch = buildChallengeFromSnapshot(snap);
                    if (ch != null) cb.onSuccess(ch);
                    else cb.onError("Izazov nije pronadjen");
                })
                .addOnFailureListener(e -> cb.onError(cleanError(e)));
    }

    public void markChallengeStarted(String challengeId, String uid, Callback cb) {
        DocumentReference challengeRef = db.collection(COL).document(challengeId);

        db.runTransaction(transaction -> {
                    DocumentSnapshot challengeSnap = transaction.get(challengeRef);
                    Challenge ch = buildChallengeFromSnapshot(challengeSnap);
                    if (ch == null) throw new IllegalStateException("Izazov nije pronadjen");
                    if (Challenge.STATUS_FINISHED.equals(ch.getStatus())) {
                        throw new IllegalStateException("Izazov je vec zavrsen");
                    }
                    if (ch.getParticipantUids() == null || !ch.getParticipantUids().contains(uid)) {
                        throw new IllegalStateException("Nisi ucesnik ovog izazova");
                    }
                    if (ch.hasSubmitted(uid)) {
                        throw new IllegalStateException("Vec si zavrsio ovaj izazov");
                    }

                    Map<String, Object> update = new HashMap<>();
                    if (Challenge.STATUS_OPEN.equals(ch.getStatus())) {
                        update.put("status", Challenge.STATUS_IN_PROGRESS);
                        update.put("startedAt", FieldValue.serverTimestamp());
                    }
                    if (!update.isEmpty()) transaction.update(challengeRef, update);
                    return null;
                })
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(cleanError(e)));
    }

    public ListenerRegistration listenChallenge(String challengeId, ChallengeCallback cb) {
        return db.collection(COL).document(challengeId)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        cb.onError(cleanError(error));
                        return;
                    }
                    Challenge ch = buildChallengeFromSnapshot(snap);
                    if (ch != null) cb.onSuccess(ch);
                });
    }

    public void submitScore(String challengeId, String uid, int score, Callback cb) {
        DocumentReference docRef = db.collection(COL).document(challengeId);

        db.runTransaction(transaction -> {
                    DocumentSnapshot snap = transaction.get(docRef);
                    if (!snap.exists()) throw new IllegalStateException("Izazov nije nadjen");

                    Challenge ch = buildChallengeFromSnapshot(snap);
                    if (ch == null) throw new IllegalStateException("Greska citanja izazova");

                    List<String> uids = ch.getParticipantUids();
                    if (uids == null || uids.isEmpty()) throw new IllegalStateException("Nema ucesnika");

                    int idx = uids.indexOf(uid);
                    if (idx < 0) throw new IllegalStateException("Nisi ucesnik ovog izazova");

                    List<Integer> scores = new ArrayList<>(ch.getParticipantScores());
                    while (scores.size() < uids.size()) scores.add(0);
                    scores.set(idx, Math.max(score, 0));

                    List<String> submittedUids = ch.getSubmittedUids() != null
                            ? new ArrayList<>(ch.getSubmittedUids())
                            : new ArrayList<>();
                    if (!submittedUids.contains(uid)) submittedUids.add(uid);

                    boolean wasAlreadyFinished = Challenge.STATUS_FINISHED.equals(ch.getStatus());
                    boolean rewardPaid = Boolean.TRUE.equals(snap.getBoolean("rewardPaid"));
                    boolean nowFinished = !wasAlreadyFinished
                            && uids.size() >= 2
                            && submittedUids.containsAll(uids);

                    Map<String, Object> update = new HashMap<>();
                    update.put("participantScores", scores);
                    update.put("submittedUids", submittedUids);
                    if (nowFinished) update.put("status", Challenge.STATUS_FINISHED);
                    transaction.update(docRef, update);

                    if (nowFinished && !rewardPaid) {
                        applyRewardsInTransaction(transaction, ch, scores);
                        transaction.update(docRef, "rewardPaid", true);
                    }

                    return null;
                })
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(cleanError(e)));
    }

    public ListenerRegistration listenChallenges(String region, ListCallback cb) {
        return db.collection(COL)
                .whereEqualTo("region", region)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener((snaps, e) -> {
                    if (e != null) {
                        if (e.getMessage() != null && e.getMessage().contains("index")) {
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(cb::onRetry, 5000);
                        } else {
                            cb.onError(cleanError(e));
                        }
                        return;
                    }
                    if (snaps == null) return;

                    List<Challenge> list = new ArrayList<>();
                    for (DocumentSnapshot snap : snaps.getDocuments()) {
                        Challenge ch = buildChallengeFromSnapshot(snap);
                        if (ch != null) list.add(ch);
                    }
                    cb.onSuccess(list);
                });
    }

    @SuppressWarnings("unchecked")
    public Challenge buildChallengeFromSnapshot(DocumentSnapshot snap) {
        if (snap == null || !snap.exists()) return null;
        try {
            Map<String, Object> data = snap.getData();
            if (data == null) return null;

            Challenge ch = new Challenge();
            ch.setId(snap.getId());
            ch.setCreatorUid((String) data.get("creatorUid"));
            ch.setCreatorUsername((String) data.get("creatorUsername"));
            ch.setRegion((String) data.get("region"));
            ch.setStatus((String) data.get("status"));

            Number starsWager = (Number) data.get("starsWager");
            if (starsWager != null) ch.setStarsWager(starsWager.intValue());
            Number tokensWager = (Number) data.get("tokensWager");
            if (tokensWager != null) ch.setTokensWager(tokensWager.intValue());
            Number createdAt = (Number) data.get("createdAt");
            if (createdAt != null) ch.setCreatedAt(createdAt.longValue());

            Object rawUids = data.get("participantUids");
            ch.setParticipantUids(rawUids instanceof List
                    ? new ArrayList<>((List<String>) rawUids)
                    : new ArrayList<>());

            Object rawNames = data.get("participantUsernames");
            ch.setParticipantUsernames(rawNames instanceof List
                    ? new ArrayList<>((List<String>) rawNames)
                    : new ArrayList<>());

            List<Integer> scores = new ArrayList<>();
            Object rawScores = data.get("participantScores");
            if (rawScores instanceof List) {
                for (Object s : (List<?>) rawScores) {
                    scores.add(s instanceof Number ? ((Number) s).intValue() : 0);
                }
            } else if (rawScores instanceof Map) {
                Map<String, Object> scoresMap = (Map<String, Object>) rawScores;
                int size = ch.getParticipantUids().size();
                for (int i = 0; i < size; i++) {
                    Object s = scoresMap.get(String.valueOf(i));
                    scores.add(s instanceof Number ? ((Number) s).intValue() : 0);
                }
            }
            while (scores.size() < ch.getParticipantUids().size()) scores.add(0);
            ch.setParticipantScores(scores);

            Object rawSubmitted = data.get("submittedUids");
            ch.setSubmittedUids(rawSubmitted instanceof List
                    ? new ArrayList<>((List<String>) rawSubmitted)
                    : new ArrayList<>());

            return ch;
        } catch (Exception ex) {
            return null;
        }
    }

    private void applyRewardsInTransaction(Transaction transaction, Challenge ch, List<Integer> finalScores) {
        List<String> uids = ch.getParticipantUids();
        int n = uids.size();
        int totalStars = ch.getStarsWager() * n;
        int totalTokens = ch.getTokensWager() * n;

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            int sa = a < finalScores.size() ? finalScores.get(a) : 0;
            int sb = b < finalScores.size() ? finalScores.get(b) : 0;
            return sb - sa;
        });

        for (int rank = 0; rank < order.length; rank++) {
            int playerIdx = order[rank];
            String playerUid = uids.get(playerIdx);
            Map<String, Object> delta = new HashMap<>();
            if (rank == 0) {
                delta.put("stars", FieldValue.increment((long) (totalStars * 0.75)));
                delta.put("tokens", FieldValue.increment((long) (totalTokens * 0.75)));
            } else if (rank == 1) {
                delta.put("stars", FieldValue.increment(ch.getStarsWager()));
                delta.put("tokens", FieldValue.increment(ch.getTokensWager()));
            }

            if (!delta.isEmpty()) {
                transaction.update(db.collection("users").document(playerUid), delta);
            }
        }
    }

    private boolean isValidWager(int stars, int tokens) {
        return stars >= 1 && stars <= MAX_STARS_WAGER
                && tokens >= 1 && tokens <= MAX_TOKENS_WAGER;
    }

    private void ensureEnoughBalance(DocumentSnapshot userSnap, int stars, int tokens) {
        if (userSnap == null || !userSnap.exists()) {
            throw new IllegalStateException("Korisnik nije pronadjen");
        }
        long currentStars = userSnap.getLong("stars") != null ? userSnap.getLong("stars") : 0L;
        long currentTokens = userSnap.getLong("tokens") != null ? userSnap.getLong("tokens") : 0L;
        if (currentStars < stars) throw new IllegalStateException("Nemas dovoljno zvezda");
        if (currentTokens < tokens) throw new IllegalStateException("Nemas dovoljno tokena");
    }

    private String cleanError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) return "Greska";
        int colon = msg.lastIndexOf(": ");
        return colon >= 0 && colon + 2 < msg.length() ? msg.substring(colon + 2) : msg;
    }

    private Map<String, Object> challengeToMap(Challenge ch) {
        Map<String, Object> m = new HashMap<>();
        m.put("creatorUid", ch.getCreatorUid());
        m.put("creatorUsername", ch.getCreatorUsername());
        m.put("region", ch.getRegion());
        m.put("starsWager", ch.getStarsWager());
        m.put("tokensWager", ch.getTokensWager());
        m.put("status", ch.getStatus());
        m.put("participantUids", ch.getParticipantUids());
        m.put("participantUsernames", ch.getParticipantUsernames());
        m.put("participantScores", ch.getParticipantScores());
        m.put("submittedUids", ch.getSubmittedUids() != null ? ch.getSubmittedUids() : new ArrayList<String>());
        m.put("createdAt", ch.getCreatedAt());
        m.put("rewardPaid", false);
        return m;
    }
}
