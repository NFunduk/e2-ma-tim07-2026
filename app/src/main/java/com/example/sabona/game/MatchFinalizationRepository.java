package com.example.sabona.game;

import com.example.sabona.leaderboard.LeaderboardRepository;
import com.example.sabona.league.League;
import com.example.sabona.league.LeagueManager;
import com.example.sabona.repository.StatsRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MatchFinalizationRepository {

    private static final String FIELD_STAR_TOKEN_REWARD_LEVEL = "starTokenRewardLevel";

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

    public void finalizeForMe(Callback callback) {
        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (myUid == null) {
            callback.onError("Nisi prijavljen/a");
            return;
        }

        String sessionId = sessionMgr.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            callback.onError("Nema sessionId-a");
            return;
        }

        db.collection(GameSessionManager.COL_GAME_SESSIONS).document(sessionId).get()
                .addOnSuccessListener(rootSnap -> {
                    if (!rootSnap.exists()) {
                        callback.onError("Sesija nije nadjena");
                        return;
                    }

                    boolean isTournamentMatch = Boolean.TRUE.equals(rootSnap.getBoolean("isTournamentMatch"));
                    if (isTournamentMatch) {
                        finalizeTournamentMatch(rootSnap, myUid, callback);
                        return;
                    }

                    boolean isFriendly = Boolean.TRUE.equals(rootSnap.getBoolean("isFriendlyMatch"));
                    long totalP1 = rootSnap.getLong("totalScoreP1") != null ? rootSnap.getLong("totalScoreP1") : 0;
                    long totalP2 = rootSnap.getLong("totalScoreP2") != null ? rootSnap.getLong("totalScoreP2") : 0;
                    String leftByUid = rootSnap.getString("leftByUid");
                    String player1Uid = rootSnap.getString("player1Uid");
                    String player2Uid = rootSnap.getString("player2Uid");
                    boolean isSoloChallenge = player1Uid != null
                            && player1Uid.equals(player2Uid)
                            && rootSnap.getString("challengeId") != null;

                    boolean isP1 = sessionMgr.isPlayer1();
                    int myScore = isSoloChallenge
                            ? (int) (totalP1 + totalP2)
                            : (int) (isP1 ? totalP1 : totalP2);
                    int oppScore = isSoloChallenge
                            ? 0
                            : (int) (isP1 ? totalP2 : totalP1);

                    boolean iLeft = myUid.equals(leftByUid);
                    boolean oppLeft = leftByUid != null && !iLeft;

                    boolean won;
                    if (oppLeft) won = true;
                    else if (iLeft) won = false;
                    else won = myScore > oppScore;

                    if (isFriendly) {
                        new com.example.sabona.daily.DailyMissionRepository()
                                .completeFriendlyMatch(myUid, null);

                        callback.onSuccess(new Result(true, won, 0, 0, myScore, oppScore));
                        return;
                    }

                    applyStarsAndTokens(myUid, won, myScore, oppScore, callback);
                })
                .addOnFailureListener(e -> callback.onError("Greska: " + e.getMessage()));
    }

    private void applyStarsAndTokens(String myUid, boolean won,
                                     int myScore, int oppScore, Callback callback) {
        db.runTransaction(transaction -> {
            DocumentReference userRef = db.collection("users").document(myUid);
            DocumentSnapshot userSnap = transaction.get(userRef);
            long oldStars = userSnap.contains("stars") && userSnap.getLong("stars") != null
                    ? userSnap.getLong("stars") : 0;
            long oldTokens = userSnap.contains("tokens") && userSnap.getLong("tokens") != null
                    ? userSnap.getLong("tokens") : 0;
            int rewardLevel = userSnap.contains(FIELD_STAR_TOKEN_REWARD_LEVEL)
                    && userSnap.getLong(FIELD_STAR_TOKEN_REWARD_LEVEL) != null
                    ? userSnap.getLong(FIELD_STAR_TOKEN_REWARD_LEVEL).intValue()
                    : (int) (oldStars / MatchRewardCalculator.STARS_PER_TOKEN);

            MatchRewardCalculator.Reward reward =
                    MatchRewardCalculator.applyRegularReward(oldStars, oldTokens, rewardLevel, won, myScore);
            League newLeague = LeagueManager.computeLeague((int) reward.newStars);

            transaction.update(userRef,
                    "stars", reward.newStars,
                    "tokens", reward.newTokens,
                    "league", newLeague.index,
                    FIELD_STAR_TOKEN_REWARD_LEVEL, reward.tokenRewardLevel);

            return new long[]{reward.starsDelta, reward.tokensGained};
        }).addOnSuccessListener(d -> {
            statsRepo.incrementGamesPlayed(won);

            if (won) {
                new com.example.sabona.daily.DailyMissionRepository()
                        .completeWinMatch(myUid, null);
            }

            int starsDelta = (int) d[0];
            new LeaderboardRepository().addStarsAfterMatch(myUid, starsDelta);

            callback.onSuccess(new Result(false, won, starsDelta, (int) d[1], myScore, oppScore));
        }).addOnFailureListener(e -> callback.onError("Greska pri upisu zvezdi: " + e.getMessage()));
    }

    private void finalizeTournamentMatch(DocumentSnapshot rootSnap,
                                         String myUid,
                                         Callback callback) {
        long totalP1 = rootSnap.getLong("totalScoreP1") != null ? rootSnap.getLong("totalScoreP1") : 0;
        long totalP2 = rootSnap.getLong("totalScoreP2") != null ? rootSnap.getLong("totalScoreP2") : 0;

        String p1 = rootSnap.getString("player1Uid");
        String p2 = rootSnap.getString("player2Uid");
        String leftByUid = rootSnap.getString("leftByUid");

        String tournamentId = rootSnap.getString("tournamentId");
        String round = rootSnap.getString("tournamentRound");
        Long bracketIndexLong = rootSnap.getLong("bracketIndex");
        int bracketIndex = bracketIndexLong != null ? bracketIndexLong.intValue() : 0;

        boolean isP1 = myUid.equals(p1);
        int myScore = (int) (isP1 ? totalP1 : totalP2);
        int oppScore = (int) (isP1 ? totalP2 : totalP1);

        boolean iLeft = myUid.equals(leftByUid);
        boolean opponentLeft = leftByUid != null && !iLeft;

        boolean won;
        if (iLeft) won = false;
        else if (opponentLeft) won = true;
        else won = myScore > oppScore;

        if (iLeft) {
            statsRepo.incrementGamesPlayed(false);
            callback.onSuccess(new Result(false, false, 0, 0, myScore, oppScore));
            return;
        }

        int regularStars = calculateRegularStars(myScore, won);
        int extraTokens = 0;
        int extraStars = 0;

        if ("semifinal".equals(round)) {
            if (!won) {
                statsRepo.incrementGamesPlayed(false);
                callback.onSuccess(new Result(false, false, 0, 0, myScore, oppScore));
                return;
            }
            extraTokens = 2;
        } else if ("final".equals(round)) {
            if (won) {
                extraTokens = 3;
                extraStars = 10;
                new com.example.sabona.daily.DailyMissionRepository()
                        .completeTournamentWin(myUid, null);
            }
        }

        int finalStars = regularStars + extraStars;

        applyTournamentReward(myUid, tournamentId, round, bracketIndex,
                won, finalStars, extraTokens, myScore, oppScore, callback);
    }

    private int calculateRegularStars(int score, boolean won) {
        return MatchRewardCalculator.regularStarsDelta(won, score);
    }

    private void applyTournamentReward(String myUid,
                                       String tournamentId,
                                       String round,
                                       int bracketIndex,
                                       boolean won,
                                       int starsDelta,
                                       int tokensDelta,
                                       int myScore,
                                       int oppScore,
                                       Callback callback) {
        String rewardDocId = round + "_" + myUid;

        db.runTransaction(transaction -> {
            DocumentReference tournamentRef = db.collection("tournaments").document(tournamentId);
            DocumentReference rewardRef = tournamentRef.collection("rewards").document(rewardDocId);
            DocumentReference userRef = db.collection("users").document(myUid);

            DocumentSnapshot rewardSnap = transaction.get(rewardRef);
            if (rewardSnap.exists()) {
                return false;
            }

            DocumentSnapshot userSnap = transaction.get(userRef);

            long oldStars = userSnap.getLong("stars") != null ? userSnap.getLong("stars") : 0;
            long oldTokens = userSnap.getLong("tokens") != null ? userSnap.getLong("tokens") : 0;

            long newStars = Math.max(0, oldStars + starsDelta);
            long tokensFromStars = (newStars / 50) - (oldStars / 50);
            if (tokensFromStars < 0) tokensFromStars = 0;

            long newTokens = oldTokens + tokensDelta + tokensFromStars;
            League newLeague = LeagueManager.computeLeague((int) newStars);

            transaction.update(userRef,
                    "stars", newStars,
                    "tokens", newTokens,
                    "league", newLeague.index);

            Map<String, Object> reward = new HashMap<>();
            reward.put("uid", myUid);
            reward.put("round", round);
            reward.put("won", won);
            reward.put("starsDelta", starsDelta);
            reward.put("tokensDelta", tokensDelta);
            reward.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(rewardRef, reward);

            if ("semifinal".equals(round) && won) {
                if (bracketIndex == 1) {
                    transaction.update(tournamentRef, "semi1WinnerUid", myUid);
                } else if (bracketIndex == 2) {
                    transaction.update(tournamentRef, "semi2WinnerUid", myUid);
                }
            }

            if ("final".equals(round) && won) {
                transaction.update(tournamentRef,
                        "winnerUid", myUid,
                        "status", "finished");
            }

            return true;
        }).addOnSuccessListener(applied -> {
            statsRepo.incrementGamesPlayed(won);

            if (starsDelta != 0) {
                new LeaderboardRepository().addStarsAfterMatch(myUid, starsDelta);
            }

            if ("semifinal".equals(round) && won) {
                tryCreateFinalIfReady(tournamentId);
            }

            callback.onSuccess(new Result(false, won, starsDelta, tokensDelta, myScore, oppScore));
        }).addOnFailureListener(e ->
                callback.onError("Greska pri turnir nagradi: " + e.getMessage()));
    }

    private void tryCreateFinalIfReady(String tournamentId) {
        DocumentReference tournamentRef = db.collection("tournaments").document(tournamentId);

        db.runTransaction(transaction -> {
            DocumentSnapshot t = transaction.get(tournamentRef);

            String w1 = t.getString("semi1WinnerUid");
            String w2 = t.getString("semi2WinnerUid");
            String existingFinal = t.getString("finalSessionId");

            if (w1 == null || w2 == null || existingFinal != null) {
                return null;
            }

            String finalSessionId = tournamentId + "_F";

            Map<String, Object> session = new HashMap<>();
            session.put("status", "active");
            session.put("isFriendlyMatch", false);
            session.put("isTournamentMatch", true);
            session.put("tournamentId", tournamentId);
            session.put("tournamentRound", "final");
            session.put("bracketIndex", 3);
            session.put("player1Uid", w1);
            session.put("player2Uid", w2);
            session.put("totalScoreP1", 0);
            session.put("totalScoreP2", 0);
            session.put("leftByUid", null);
            session.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(db.collection(GameSessionManager.COL_GAME_SESSIONS).document(finalSessionId), session);

            transaction.update(tournamentRef,
                    "finalSessionId", finalSessionId,
                    "status", "final");

            transaction.update(db.collection("tournamentQueue").document(w1),
                    "status", "matched",
                    "sessionId", finalSessionId);

            transaction.update(db.collection("tournamentQueue").document(w2),
                    "status", "matched",
                    "sessionId", finalSessionId);

            return null;
        });
    }
}
