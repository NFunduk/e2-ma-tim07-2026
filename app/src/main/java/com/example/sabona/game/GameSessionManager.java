package com.example.sabona.game;

import com.google.firebase.auth.FirebaseAuth;

public class GameSessionManager {

    // Isti format kao KoZnaZna — "gameSessions/{sessionId}/games/{game}"
    public static final String COL_GAME_SESSIONS = "gameSessions";

    // Poznate konstante za poređenje u GameState — ovi stringovi se snimaju u Firestore
    public static final String ROLE_PLAYER1 = "player1";
    public static final String ROLE_PLAYER2 = "player2";

    // Dinamički se popunjavaju pri init-u
    private String sessionId;
    private String myRole;        // ROLE_PLAYER1 ili ROLE_PLAYER2
    private String player1Uid;
    private String player2Uid;

    private static GameSessionManager instance;

    private GameSessionManager() {}

    public static GameSessionManager get() {
        if (instance == null) instance = new GameSessionManager();
        return instance;
    }

    /** Poziva host pri kreiranju sesije */
    public void setupAsHost(String sessionId) {
        this.sessionId  = sessionId;
        this.myRole     = ROLE_PLAYER1;
        this.player1Uid = getMyUid();
        this.player2Uid = null;
    }

    /** Poziva guest pri pridruživanju */
    public void setupAsGuest(String sessionId, String hostUid) {
        this.sessionId  = sessionId;
        this.myRole     = ROLE_PLAYER2;
        this.player1Uid = hostUid;
        this.player2Uid = getMyUid();
    }

    public String getSessionId()  { return sessionId;  }
    public String getMyRole()     { return myRole;      }
    public String getPlayer1Uid() { return player1Uid;  }
    public String getPlayer2Uid() { return player2Uid;  }

    public String getMyUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return "";
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public boolean isPlayer1() {
        return ROLE_PLAYER1.equals(myRole);
    }

    public int getMyPlayerNumber() {
        return isPlayer1() ? 1 : 2;
    }

    public int getOpponentPlayerNumber() {
        return isPlayer1() ? 2 : 1;
    }

    /** Jedinstveni uid koji se upisuje u Firestore za "activePlayerUid" */
    public String getUidForRole(String role) {
        return ROLE_PLAYER1.equals(role) ? player1Uid : player2Uid;
    }

    /** Je li ovaj uid moj? */
    public boolean isMe(String uid) {
        return uid != null && uid.equals(getMyUid());
    }

    public void reset() {
        instance = null;
    }
}