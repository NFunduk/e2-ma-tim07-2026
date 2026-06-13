package com.example.sabona.asocijacije;

import com.example.sabona.game.GameSessionManager;
import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class AsocijacijeGameState {

    public String status = "playing";
    public String phase = "WAITING_P2";
    // WAITING_P2 | PLAYING | ROUND_END | GAME_OVER

    public int round = 1;
    public String activePlayerRole = GameSessionManager.ROLE_PLAYER1;

    public String game0Id = null;
    public String game1Id = null;

    public int player1Score = 0;
    public int player2Score = 0;

    public List<Boolean> opened = new ArrayList<>();
    public List<Boolean> columnSolved = new ArrayList<>();

    public boolean finalSolved = false;
    public boolean fieldOpenedThisTurn = false;
    public boolean roundFinished = false;

    public long phaseEndsAtMillis = 0L;

    public String hostUid = null;
    public Timestamp updatedAt = null;


    public AsocijacijeGameState() {}
}