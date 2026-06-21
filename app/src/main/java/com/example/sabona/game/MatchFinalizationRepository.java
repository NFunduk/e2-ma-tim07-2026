package com.example.sabona.game;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.sabona.repository.StatsRepository;

public class MatchFinalizationRepository {

    public static class Result {
        public final boolean friendly;
        public final boolean won;
        public final int starsDelta;
        public final int tokensGained;
        public final int myTotalScore;
        public final int opponentTotalScore;

        public Result(boolean friendly, boolean won, int starsDelta, int tokensGained,
                      int myTotalScore, int opponentTotalScore) {
            this.friendly = friendly;
            this.won = won;
            this.starsDelta = starsDelta;
            this.tokensGained = tokensGained;
            this.myTotalScore = myTotalScore;
            this.opponentTotalScore = opponentTotalScore;
        }
    }

    public interface Callback {
        void onSuccess(Result result);
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final GameSessionManager sessionMgr = GameSessionManager.get();
    private final StatsRepository statsRepo = new StatsRepository();

    /** Zove ga SVAKI klijent nezavisno nakon GAME_OVER poslednje igre (Moj broj). */
    public void finalizeForMe(Callback callback) {
        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (myUid == null) { callback.onError("Nisi prijavljen/a"); return; }

        String sessionId = sessionMgr.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) { callback.onError("Nema sessionId-a"); return; }

        db.collection(GameSessionManager.COL_GAME_SESSIONS).document(sessionId).get()
                .addOnSuccessListener(rootSnap -> {
                    if (!rootSnap.exists()) { callback.onError("Sesija nije nađena"); return; }

                    boolean isFriendly = Boolean.TRUE.equals(rootSnap.getBoolean("isFriendlyMatch"));
                    long totalP1 = rootSnap.getLong("totalScoreP1") != null ? rootSnap.getLong("totalScoreP1") : 0;
                    long totalP2 = rootSnap.getLong("totalScoreP2") != null ? rootSnap.getLong("totalScoreP2") : 0;
                    String leftByUid = rootSnap.getString("leftByUid");

                    boolean isP1 = sessionMgr.isPlayer1();
                    int myScore  = (int) (isP1 ? totalP1 : totalP2);
                    int oppScore = (int) (isP1 ? totalP2 : totalP1);

                    boolean iLeft   = myUid.equals(leftByUid);
                    boolean oppLeft = leftByUid != null && !iLeft;

                    boolean won;
                    if (oppLeft)    won = true;
                    else if (iLeft) won = false;
                    else            won = myScore > oppScore; // remi → tretira se kao gubitak (formula ispod)

                    if (isFriendly) {
                        callback.onSuccess(new Result(true, won, 0, 0, myScore, oppScore));
                        return;
                    }

                    if (iLeft) {
                        // Napustio/la si partiju — gubiš je i ne dobijaš zvezde (ni gubitničke)
                        statsRepo.incrementGamesPlayed(false);
                        callback.onSuccess(new Result(false, false, 0, 0, myScore, oppScore));
                        return;
                    }

                    int bonus = myScore / 40;
                    int rawDelta = won ? (10 + bonus) : (-10 + bonus);
                    applyStarsAndTokens(myUid, rawDelta, won, myScore, oppScore, callback);
                })
                .addOnFailureListener(e -> callback.onError("Greška: " + e.getMessage()));
    }

    private void applyStarsAndTokens(String myUid, int rawDelta, boolean won,
                                     int myScore, int oppScore, Callback callback) {
        db.runTransaction(transaction -> {
            DocumentSnapshot userSnap = transaction.get(db.collection("users").document(myUid));
            long oldStars  = userSnap.contains("stars")  && userSnap.getLong("stars")  != null ? userSnap.getLong("stars")  : 0;
            long oldTokens = userSnap.contains("tokens") && userSnap.getLong("tokens") != null ? userSnap.getLong("tokens") : 0;

            long newStars = oldStars + rawDelta;
            if (newStars < 0) newStars = 0; // "ako nema do tada zvezde ne može ih ni izgubiti"

            long tokensEarned = (newStars / 50) - (oldStars / 50);
            if (tokensEarned < 0) tokensEarned = 0;
            long newTokens = oldTokens + tokensEarned;

            transaction.update(db.collection("users").document(myUid),
                    "stars", newStars, "tokens", newTokens);

            return new long[]{newStars - oldStars, tokensEarned};
        }).addOnSuccessListener(d -> {
            statsRepo.incrementGamesPlayed(won);
            callback.onSuccess(new Result(false, won, (int) d[0], (int) d[1], myScore, oppScore));
        }).addOnFailureListener(e -> callback.onError("Greška pri upisu zvezdi: " + e.getMessage()));
    }
}