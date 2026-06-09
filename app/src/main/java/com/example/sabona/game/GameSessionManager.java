package com.example.sabona.game;

import com.google.firebase.auth.FirebaseAuth;

public class GameSessionManager {

    public static final String GAME_ID = "demo_game_001";

    public static final String UID_PLAYER1 = "ZrL8Iht5Pvh2KKvoiZeFsbEZ2GT2"; // bosa
    public static final String UID_PLAYER2 = "qqkBI2y0SDQLwFX5mtFMYLfQgDk1"; // nadja

    private static GameSessionManager instance;

    private GameSessionManager() {}

    public static GameSessionManager get() {
        if (instance == null) instance = new GameSessionManager();
        return instance;
    }

    public String getMyUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return "";
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    /** Vraca 1 ako si player1 (bosa), 2 ako si player2 (nadja) */
    public int getMyPlayerNumber() {
        return getMyUid().equals(UID_PLAYER1) ? 1 : 2;
    }

    public int getOpponentPlayerNumber() {
        return getMyPlayerNumber() == 1 ? 2 : 1;
    }

    public boolean isPlayer1() {
        return getMyPlayerNumber() == 1;
    }
}