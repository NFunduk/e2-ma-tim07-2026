package com.example.sabona.viewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.game.AsocijacijeGameState;
import com.example.sabona.game.GameSessionManager;
import com.example.sabona.game.GameSessionRepository;
import com.example.sabona.model.AssociationGame;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AsocijacijeViewModel extends ViewModel {

    public enum Phase {
        LOADING,
        WAITING_P2,
        PLAYING,
        ROUND_END,
        GAME_OVER
    }

    private final MutableLiveData<Phase> phase = new MutableLiveData<>(Phase.LOADING);
    private final MutableLiveData<AsocijacijeGameState> stateLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> myTurn = new MutableLiveData<>(false);

    private final GameSessionRepository sessionRepo = new GameSessionRepository();
    private final GameSessionManager sessionMgr = GameSessionManager.get();

    private ListenerRegistration listener;
    private AsocijacijeGameState remoteState;

    public LiveData<Phase> getPhase() {
        return phase;
    }

    public LiveData<AsocijacijeGameState> getState() {
        return stateLiveData;
    }

    public LiveData<Boolean> getMyTurn() {
        return myTurn;
    }

    public void init(List<AssociationGame> games) {
        phase.setValue(Phase.LOADING);

        if (sessionMgr.isPlayer1()) {
            setupAsHost(games);
        } else {
            setupAsGuest();
        }
    }

    private void setupAsHost(List<AssociationGame> games) {
        AsocijacijeGameState state = new AsocijacijeGameState();

        state.status = "playing";
        state.phase = "WAITING_P2";
        state.round = 1;
        state.activePlayerRole = GameSessionManager.ROLE_PLAYER1;

        if (games != null && games.size() >= 2) {
            state.game0Id = games.get(0).docId;
            state.game1Id = games.get(1).docId;
        }

        state.player1Score = 0;
        state.player2Score = 0;

        state.opened = createBoolList(16, false);
        state.columnSolved = createBoolList(4, false);

        state.finalSolved = false;
        state.fieldOpenedThisTurn = false;
        state.roundFinished = false;
        state.phaseEndsAtMillis = System.currentTimeMillis() + 120000;
        state.hostUid = sessionMgr.getMyUid();

        sessionRepo.initAsocijacijeState(state);
        startListening();
    }

    private void setupAsGuest() {
        startListening();
    }

    private void startListening() {
        if (listener != null) listener.remove();

        listener = sessionRepo.listenAsocijacije((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;

            AsocijacijeGameState state = snapshot.toObject(AsocijacijeGameState.class);
            if (state == null) return;

            remoteState = state;
            stateLiveData.setValue(state);

            if ("WAITING_P2".equals(state.phase)) {
                if (!sessionMgr.isPlayer1()) {
                    state.phase = "PLAYING";
                    sessionRepo.updateAsocijacijeState(state);
                    return;
                }

                phase.setValue(Phase.WAITING_P2);
            } else if ("PLAYING".equals(state.phase)) {
                phase.setValue(Phase.PLAYING);
            } else if ("ROUND_END".equals(state.phase)) {
                phase.setValue(Phase.ROUND_END);
            } else if ("GAME_OVER".equals(state.phase)) {
                phase.setValue(Phase.GAME_OVER);
            }

            boolean turn = state.activePlayerRole.equals(sessionMgr.getMyRole());
            myTurn.setValue(turn);
        });
    }

    public void openField(int col, int row) {
        if (remoteState == null) return;

        int index = col * 4 + row;
        remoteState.opened.set(index, true);
        remoteState.fieldOpenedThisTurn = true;

        sessionRepo.updateAsocijacijeState(remoteState);
    }

    public void solveColumn(int col, int points) {
        if (remoteState == null) return;

        remoteState.columnSolved.set(col, true);

        for (int row = 0; row < 4; row++) {
            int index = col * 4 + row;
            remoteState.opened.set(index, true);
        }

        addPointsLocal(points);
        remoteState.fieldOpenedThisTurn = false;

        sessionRepo.updateAsocijacijeState(remoteState);
    }

    public void solveFinal(int points) {
        if (remoteState == null) return;

        remoteState.finalSolved = true;
        addPointsLocal(points);
        remoteState.phase = "ROUND_END";
        remoteState.roundFinished = true;
        remoteState.phaseEndsAtMillis = System.currentTimeMillis() + 6000;

        sessionRepo.updateAsocijacijeState(remoteState);
    }

    public void passTurn() {
        if (remoteState == null) return;

        if (GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole)) {
            remoteState.activePlayerRole = GameSessionManager.ROLE_PLAYER2;
        } else {
            remoteState.activePlayerRole = GameSessionManager.ROLE_PLAYER1;
        }

        remoteState.fieldOpenedThisTurn = false;
        sessionRepo.updateAsocijacijeState(remoteState);
    }

    public void finishRound() {
        if (remoteState == null) return;
        if ("ROUND_END".equals(remoteState.phase) || "GAME_OVER".equals(remoteState.phase)) return;

        remoteState.phase = "ROUND_END";
        remoteState.roundFinished = true;
        remoteState.fieldOpenedThisTurn = false;

        remoteState.phaseEndsAtMillis = System.currentTimeMillis() + 6000;

        sessionRepo.updateAsocijacijeState(remoteState);
    }

    public void startNextRound() {
        if (remoteState == null) return;

        if (remoteState.round == 1) {
            remoteState.round = 2;
            remoteState.phase = "PLAYING";
            remoteState.activePlayerRole = GameSessionManager.ROLE_PLAYER2;

            remoteState.opened = createBoolList(16, false);
            remoteState.columnSolved = createBoolList(4, false);

            remoteState.finalSolved = false;
            remoteState.fieldOpenedThisTurn = false;
            remoteState.roundFinished = false;
            remoteState.phaseEndsAtMillis = System.currentTimeMillis() + 120000;
        } else {
            remoteState.phase = "GAME_OVER";
            remoteState.status = "finished";
        }

        sessionRepo.updateAsocijacijeState(remoteState);
    }

    private void addPointsLocal(int points) {
        if (GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole)) {
            remoteState.player1Score += points;
        } else {
            remoteState.player2Score += points;
        }
    }

    private List<Boolean> createBoolList(int size, boolean value) {
        List<Boolean> list = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            list.add(value);
        }

        return list;
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        if (listener != null) {
            listener.remove();
        }
    }
}