package com.example.sabona.game;

import com.google.firebase.Timestamp;

public class MojBrojGameState {

    public String status = "waiting"; // "waiting" | "playing" | "finished"

    // 1 ili 2
    public int round = 1;

    // uid aktivnog igrača (čija je runda)
    public String activePlayerUid = GameSessionManager.UID_PLAYER1;

    // "IDLE" | "REVEAL_TARGET" | "PLAYING" | "ROUND_END" | "GAME_OVER"
    public String phase = "IDLE";

    public int targetNumber = 0;

    // kao String "1,2,3,4,5,6" jer Firestore arrays su ok ali ovako je jednostavnije
    public String offeredNumbers = "";

    public int player1Score = 0;
    public int player2Score = 0;

    // -1 = nije uneto, 0+ = rezultat izraza
    public int player1RoundResult = -1;
    public int player2RoundResult = -1;

    public Timestamp updatedAt = null;

    public MojBrojGameState() {}
}