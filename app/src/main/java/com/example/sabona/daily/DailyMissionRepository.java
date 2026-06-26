package com.example.sabona.daily;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DailyMissionRepository {

    public static final String MISSION_WIN_MATCH = "missionWinMatch";
    public static final String MISSION_SEND_CHAT = "missionSendChat";
    public static final String MISSION_FRIENDLY_MATCH = "missionFriendlyMatch";
    public static final String MISSION_TOURNAMENT_WIN = "missionTournamentWin";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public void ensureTodayMissions(String uid) {
        if (uid == null || uid.trim().isEmpty()) return;

        DocumentReference ref = db.collection("users").document(uid);
        String today = getToday();

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(ref);

            String missionDate = snap.getString("missionDate");

            if (!today.equals(missionDate)) {
                Map<String, Object> data = new HashMap<>();
                data.put("missionDate", today);
                data.put(MISSION_WIN_MATCH, false);
                data.put(MISSION_SEND_CHAT, false);
                data.put(MISSION_FRIENDLY_MATCH, false);
                data.put(MISSION_TOURNAMENT_WIN, false);
                data.put("missionBonusClaimed", false);

                transaction.set(ref, data, SetOptions.merge());
            }

            return null;
        });
    }

    public void completeWinMatch(String uid, Callback callback) {
        completeMission(uid, MISSION_WIN_MATCH, callback);
    }

    public void completeSendChat(String uid, Callback callback) {
        completeMission(uid, MISSION_SEND_CHAT, callback);
    }

    public void completeFriendlyMatch(String uid, Callback callback) {
        completeMission(uid, MISSION_FRIENDLY_MATCH, callback);
    }

    public void completeTournamentWin(String uid, Callback callback) {
        completeMission(uid, MISSION_TOURNAMENT_WIN, callback);
    }

    private void completeMission(String uid, String missionField, Callback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            if (callback != null) callback.onError("Korisnik nije pronađen");
            return;
        }

        DocumentReference ref = db.collection("users").document(uid);
        String today = getToday();

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(ref);

            String missionDate = snap.getString("missionDate");

            boolean winMatch = getBool(snap, MISSION_WIN_MATCH);
            boolean sendChat = getBool(snap, MISSION_SEND_CHAT);
            boolean friendlyMatch = getBool(snap, MISSION_FRIENDLY_MATCH);
            boolean tournamentWin = getBool(snap, MISSION_TOURNAMENT_WIN);
            boolean bonusClaimed = getBool(snap, "missionBonusClaimed");

            if (!today.equals(missionDate)) {
                winMatch = false;
                sendChat = false;
                friendlyMatch = false;
                tournamentWin = false;
                bonusClaimed = false;

                Map<String, Object> resetData = new HashMap<>();
                resetData.put("missionDate", today);
                resetData.put(MISSION_WIN_MATCH, false);
                resetData.put(MISSION_SEND_CHAT, false);
                resetData.put(MISSION_FRIENDLY_MATCH, false);
                resetData.put(MISSION_TOURNAMENT_WIN, false);
                resetData.put("missionBonusClaimed", false);

                transaction.set(ref, resetData, SetOptions.merge());
            }

            boolean alreadyCompleted = false;

            if (MISSION_WIN_MATCH.equals(missionField)) alreadyCompleted = winMatch;
            if (MISSION_SEND_CHAT.equals(missionField)) alreadyCompleted = sendChat;
            if (MISSION_FRIENDLY_MATCH.equals(missionField)) alreadyCompleted = friendlyMatch;
            if (MISSION_TOURNAMENT_WIN.equals(missionField)) alreadyCompleted = tournamentWin;

            if (alreadyCompleted) {
                return null;
            }

            transaction.update(ref,
                    missionField, true,
                    "stars", FieldValue.increment(3),
                    "weeklyStars", FieldValue.increment(3),
                    "monthlyStars", FieldValue.increment(3)
            );

            if (MISSION_WIN_MATCH.equals(missionField)) winMatch = true;
            if (MISSION_SEND_CHAT.equals(missionField)) sendChat = true;
            if (MISSION_FRIENDLY_MATCH.equals(missionField)) friendlyMatch = true;
            if (MISSION_TOURNAMENT_WIN.equals(missionField)) tournamentWin = true;

            boolean allCompleted = winMatch && sendChat && friendlyMatch && tournamentWin;

            if (allCompleted && !bonusClaimed) {
                transaction.update(ref,
                        "tokens", FieldValue.increment(2),
                        "stars", FieldValue.increment(3),
                        "weeklyStars", FieldValue.increment(3),
                        "monthlyStars", FieldValue.increment(3),
                        "missionBonusClaimed", true
                );
            }

            return null;
        }).addOnSuccessListener(unused -> {
            if (callback != null) callback.onSuccess();
        }).addOnFailureListener(e -> {
            if (callback != null) callback.onError(e.getMessage());
        });
    }

    private boolean getBool(com.google.firebase.firestore.DocumentSnapshot snap, String field) {
        Boolean value = snap.getBoolean(field);
        return value != null && value;
    }

    private String getToday() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new java.util.Date());
    }
}