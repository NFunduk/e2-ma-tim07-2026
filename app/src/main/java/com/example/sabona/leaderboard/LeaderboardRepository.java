package com.example.sabona.leaderboard;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import com.example.sabona.repository.NotificationFactory;
import com.example.sabona.repository.NotificationRepository;
import com.google.firebase.firestore.FieldValue;

public class LeaderboardRepository {

    public interface LeaderboardCallback {
        void onSuccess(List<LeaderboardEntry> entries);
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadWeeklyLeaderboard(@NonNull LeaderboardCallback callback) {
        loadLeaderboard("weeklyStars", "weeklyGamesPlayed", callback);
    }

    public void loadMonthlyLeaderboard(@NonNull LeaderboardCallback callback) {
        loadLeaderboard("monthlyStars", "monthlyGamesPlayed", callback);
    }

    private void loadLeaderboard(String starsField,
                                 String gamesPlayedField,
                                 LeaderboardCallback callback) {

        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LeaderboardEntry> list = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String uid = doc.getId();
                        String username = doc.getString("username");

                        Long leagueLong = doc.getLong("league");
                        Long starsLong = doc.getLong(starsField);
                        Long gamesLong = doc.getLong(gamesPlayedField);

                        int league = leagueLong != null ? leagueLong.intValue() : 0;
                        int stars = starsLong != null ? starsLong.intValue() : 0;
                        int games = gamesLong != null ? gamesLong.intValue() : 0;

                        if (games <= 0) continue;

                        if (username == null || username.trim().isEmpty()) {
                            username = "Nepoznat igrač";
                        }

                        list.add(new LeaderboardEntry(uid, username, league, stars, games));
                    }

                    list.sort((a, b) -> Integer.compare(b.getStars(), a.getStars()));

                    if (list.size() > 100) {
                        list = list.subList(0, 100);
                    }

                    callback.onSuccess(list);
                })
                .addOnFailureListener(e ->
                        callback.onError("Greška pri učitavanju rang liste: " + e.getMessage()));
    }

    public void addStarsAfterMatch(String uid, int earnedStars) {
        if (uid == null || uid.trim().isEmpty()) return;

        db.collection("users")
                .document(uid)
                .update(
                        "weeklyStars", FieldValue.increment(earnedStars),
                        "monthlyStars", FieldValue.increment(earnedStars),
                        "weeklyGamesPlayed", FieldValue.increment(1),
                        "monthlyGamesPlayed", FieldValue.increment(1)
                );
    }

    public void distributeWeeklyRewards() {
        distributeRewards("weeklyStars", "weeklyGamesPlayed", "nedeljnoj", true);
    }

    public void distributeMonthlyRewards() {
        distributeRewards("monthlyStars", "monthlyGamesPlayed", "mesečnoj", false);
    }

    private void distributeRewards(String starsField,
                                   String gamesPlayedField,
                                   String cycleName,
                                   boolean weekly) {

        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LeaderboardEntry> list = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String uid = doc.getId();
                        String username = doc.getString("username");

                        Long leagueLong = doc.getLong("league");
                        Long starsLong = doc.getLong(starsField);
                        Long gamesLong = doc.getLong(gamesPlayedField);

                        int league = leagueLong != null ? leagueLong.intValue() : 0;
                        int stars = starsLong != null ? starsLong.intValue() : 0;
                        int games = gamesLong != null ? gamesLong.intValue() : 0;

                        if (games <= 0) continue;
                        if (username == null || username.trim().isEmpty()) {
                            username = "Nepoznat igrač";
                        }

                        list.add(new LeaderboardEntry(uid, username, league, stars, games));
                    }

                    list.sort((a, b) -> Integer.compare(b.getStars(), a.getStars()));

                    int max = Math.min(list.size(), 10);
                    NotificationRepository notificationRepository = new NotificationRepository();

                    for (int i = 0; i < max; i++) {
                        LeaderboardEntry entry = list.get(i);
                        int position = i + 1;
                        int rewardTokens = getRewardTokens(position, weekly);

                        if (rewardTokens <= 0) continue;

                        db.collection("users")
                                .document(entry.getUserId())
                                .update("tokens", FieldValue.increment(rewardTokens));

                        notificationRepository.createNotification(
                                entry.getUserId(),
                                NotificationFactory.leaderboardReward(cycleName, position, rewardTokens)
                        );
                    }
                });
    }

    private int getRewardTokens(int position, boolean weekly) {
        if (position == 1) return weekly ? 5 : 10;
        if (position == 2) return weekly ? 3 : 6;
        if (position == 3) return weekly ? 2 : 4;
        if (position >= 4 && position <= 10) return weekly ? 1 : 2;
        return 0;
    }

    public void resetWeeklyCycle() {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {
                        doc.getReference().update(
                                "weeklyStars", 0,
                                "weeklyGamesPlayed", 0
                        );
                    }
                });
    }

    public void resetMonthlyCycle() {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {
                        doc.getReference().update(
                                "monthlyStars", 0,
                                "monthlyGamesPlayed", 0
                        );
                    }
                });
    }
}