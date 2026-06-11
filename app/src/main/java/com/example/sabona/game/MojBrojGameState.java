package com.example.sabona.game;

import com.google.firebase.Timestamp;

/**
 * Firestore state za Moj Broj.
 *
 * activePlayerRole = "player1" | "player2" (umesto UID — konzistentno s KoZnaZna)
 */
public class MojBrojGameState {

    public String status = "waiting"; // "waiting" | "playing" | "finished"

    public int round = 1; // 1 ili 2

    // "player1" | "player2" — ko stopira (čija je runda)
    public String activePlayerRole = GameSessionManager.ROLE_PLAYER1;

    // "WAITING_P2"   - čekamo da se guest pridruži
    // "IDLE"         - čekamo STOP za traženi broj
    // "REVEAL_TARGET"- traženi broj otkriven, čekamo STOP za ponuđene
    // "PLAYING"      - OBA igrača unose izraz (60s)
    // "ROUND_END"    - runda gotova
    // "GAME_OVER"    - obe runde gotove
    public String phase = "WAITING_P2";

    public int targetNumber = 0;

    // "1,2,3,4,5,6"
    public String offeredNumbers = "";

    public int player1Score = 0;
    public int player2Score = 0;

    // -1 = nije predao, 0+ = evaluiran rezultat izraza
    public int player1RoundResult = -1;
    public int player2RoundResult = -1;

    // false = još igra, true = predao (submit ili timer)
    public boolean player1Done = false;
    public boolean player2Done = false;

    // UID hosta — guest ga čita pri pridruživanju
    public String hostUid = null;

    public Timestamp updatedAt = null;

    public MojBrojGameState() {}
}