package com.example.sabona.game;

import com.google.firebase.Timestamp;

public class MojBrojGameState {

    public String status = "waiting"; // "waiting" | "playing" | "finished"

    // 1 ili 2
    public int round = 1;

    // uid igrača ČIJA je runda (ko je stopirao, ko vidi STOP dugme)
    // U MojBroj: runda 1 = player1 stopira, runda 2 = player2 stopira
    public String activePlayerUid = GameSessionManager.UID_PLAYER1;

    // "IDLE"         - čekamo STOP za traženi broj (samo activePlayer može)
    // "REVEAL_TARGET"- traženi broj otkriven, čekamo STOP za ponuđene (samo activePlayer)
    // "PLAYING"      - OBA igrača unose izraz istovremeno (60s tajmer)
    // "ROUND_END"    - runda gotova, prikazujemo rezultate
    // "GAME_OVER"    - obe runde gotove
    public String phase = "IDLE";

    public int targetNumber = 0;

    // kao String "1,2,3,4,5,6"
    public String offeredNumbers = "";

    public int player1Score = 0;
    public int player2Score = 0;

    // -1 = nije uneo ništa / još nije završio
    // 0+ = rezultat evaluiranog izraza
    public int player1RoundResult = -1;
    public int player2RoundResult = -1;

    // Indicator da je igrač predao (submit ili timer istekao)
    // false = još igra / nije ni počeo, true = gotov
    public boolean player1Done = false;
    public boolean player2Done = false;

    public Timestamp updatedAt = null;

    public MojBrojGameState() {}
}