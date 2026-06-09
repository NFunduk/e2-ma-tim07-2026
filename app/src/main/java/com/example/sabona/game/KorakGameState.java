package com.example.sabona.game;

import com.google.firebase.Timestamp;

public class KorakGameState {

    // "waiting" | "playing" | "finished"
    public String status = "waiting";

    // 1 = runda 1 (igra player1), 2 = runda 2 (igra player2)
    public int round = 1;

    // uid igrača čija je runda
    public String activePlayerUid = GameSessionManager.UID_PLAYER1;

    // "MAIN" | "BONUS" | "ROUND_END" | "GAME_OVER"
    public String phase = "MAIN";

    // koliko koraka je otkriveno (1–7)
    public int stepsRevealed = 1;

    // index igre iz korak_po_korak kolekcije (0 ili 1)
    public int gameIndex = 0;

    public int player1Score = 0;
    public int player2Score = 0;

    // null = nije odgovorio, neprazan string = odgovor
    public String player1Answer = null;
    public String player2Answer = null;

    // "correct" | "wrong" | null — rezultat poslednjeg odgovora
    public String lastAnswerResult = null;

    // koji igrac je dao poslednji odgovor (za prikaz)
    public int lastAnswerPlayer = 0;

    public int lastPointsAwarded = 0;

    public Timestamp updatedAt = null;

    public KorakGameState() {}
}