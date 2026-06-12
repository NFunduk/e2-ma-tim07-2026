package com.example.sabona.game;

import com.google.firebase.Timestamp;

/**
 * Firestore state za Korak po korak.
 *
 * activePlayerRole = "player1" | "player2"  (umesto UID — konzistentno s KoZnaZna)
 */
public class KorakGameState {

    public String status = "waiting"; // "waiting" | "playing" | "finished"

    public int round = 1; // 1 ili 2

    // "player1" | "player2" — čija je runda
    public String activePlayerRole = GameSessionManager.ROLE_PLAYER1;

    // "WAITING_P2" | "MAIN" | "BONUS" | "ROUND_END" | "GAME_OVER"
    public String phase = "WAITING_P2";

    // koliko koraka je otkriveno (1–7)
    public int stepsRevealed = 1;

    // index igre iz kolekcije (0 ili 1)
    public int gameIndex = 0;

    // ID-ovi pitanja koje je host izabrao (da oba igrača imaju isti redosled — kao KoZnaZna)
    public String game0Id = null;
    public String game1Id = null;

    public int player1Score = 0;
    public int player2Score = 0;

    // Na kom koraku je player1 pogodio (0 = nije pogodio, 1-7 = korak)
    // Koristi se za statistiku profila (Student 2)
    public int player1GuessedAtStep = 0;

    // null = nije odgovorio
    public String player1Answer = null;
    public String player2Answer = null;

    // "correct" | "wrong" | null
    public String lastAnswerResult = null;
    public int    lastAnswerPlayer = 0;
    public int    lastPointsAwarded = 0;

    // UID hosta (player1) — guest ga čita pri pridruživanju
    public String hostUid = null;

    public Timestamp updatedAt = null;

    public KorakGameState() {}
}