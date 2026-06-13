package com.example.sabona.skocko;

import com.example.sabona.game.GameSessionManager;
import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class SkockoGameState {

    public String status = "playing";
    public String phase = "WAITING_P2";
    // WAITING_P2 | PLAYING | ROUND_END | GAME_OVER

    public int round = 1;
    public String activePlayerRole = GameSessionManager.ROLE_PLAYER1;

    public int player1Score = 0;
    public int player2Score = 0;

    public String secret = "";

    public int attempt = 0;
    public boolean opponentChance = false;
    public boolean roundFinished = false;

    public List<String> rows = new ArrayList<>();

    public String hostUid = null;
    public Timestamp updatedAt = null;

    public String currentGuess = "";
    public int lastCorrectPlace = 0;
    public int lastCorrectSymbol = 0;

    public SkockoGameState() {}
}