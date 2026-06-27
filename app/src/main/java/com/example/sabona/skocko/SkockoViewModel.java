package com.example.sabona.skocko;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.game.GameSessionManager;
import com.example.sabona.game.GameSessionRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Random;

public class SkockoViewModel extends ViewModel {

    public enum Phase {
        LOADING,
        WAITING_P2,
        PLAYING,
        ROUND_END,
        GAME_OVER
    }

    private final MutableLiveData<Phase> phase = new MutableLiveData<>(Phase.LOADING);
    private final MutableLiveData<SkockoGameState> stateLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> myTurn = new MutableLiveData<>(false);

    private final GameSessionRepository sessionRepo = new GameSessionRepository();
    private final GameSessionManager sessionMgr = GameSessionManager.get();

    private ListenerRegistration listener;
    private SkockoGameState remoteState;

    // nova polja
    private boolean opponentHasLeft = false;
    private ListenerRegistration abandonListener;

    private final String[] symbols = {"☻", "■", "●", "♥", "▲", "★"};

    public LiveData<Phase> getPhase() {
        return phase;
    }

    public LiveData<SkockoGameState> getState() {
        return stateLiveData;
    }

    public LiveData<Boolean> getMyTurn() {
        return myTurn;
    }

    public void init() {
        phase.setValue(Phase.LOADING);
        listenForAbandon();

        if (sessionMgr.isPlayer1()) {
            setupAsHost();
        } else {
            setupAsGuest();
        }
    }

    private void listenForAbandon() {
        abandonListener = sessionRepo.listenRootSession((snap, e) -> {
            if (snap == null || !snap.exists()) return;
            String leftUid = snap.getString("leftByUid");
            if (leftUid == null || sessionMgr.isMe(leftUid)) return;
            opponentHasLeft = true;
            if (remoteState == null && !sessionMgr.isPlayer1()) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (remoteState == null) setupAsRemainingPlayer();
                }, 500);
            }
        });
    }

    public boolean amIAuthoritative() {
        return sessionMgr.isPlayer1() || opponentHasLeft;
    }

    private void setupAsHost() {
        SkockoGameState state = new SkockoGameState();

        state.status = "playing";
        state.phase = GameSessionManager.get().isSoloSession() ? "IDLE" : "WAITING_P2";
        state.round = 1;
        state.activePlayerRole = GameSessionManager.ROLE_PLAYER1;
        state.player1Score = 0;
        state.player2Score = 0;
        state.secret = generateSecretAsString();
        state.attempt = 0;
        state.opponentChance = false;
        state.roundFinished = false;
        state.hostUid = sessionMgr.getMyUid();

        sessionRepo.initSkockoState(state);
        startListening();
    }

    private void setupAsRemainingPlayer() {
        SkockoGameState state = new SkockoGameState();

        state.status = "playing";
        state.phase = "PLAYING";
        state.round = 1;
        state.activePlayerRole = sessionMgr.getMyRole();
        state.player1Score = 0;
        state.player2Score = 0;
        state.secret = generateSecretAsString();
        state.attempt = 0;
        state.opponentChance = false;
        state.roundFinished = false;
        state.hostUid = sessionMgr.getMyUid();

        sessionRepo.initSkockoState(state);
        startListening();
    }

    private void setupAsGuest() {
        startListening();
    }

    private void startListening() {
        if (listener != null) listener.remove();

        listener = sessionRepo.listenSkocko((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;

            SkockoGameState state = snapshot.toObject(SkockoGameState.class);
            if (state == null) return;

            remoteState = state;
            stateLiveData.setValue(state);

            if ("WAITING_P2".equals(state.phase)) {
                if (!sessionMgr.isPlayer1()) {
                    state.phase = "PLAYING";
                    sessionRepo.updateSkockoState(state);
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

            boolean turn = GameSessionManager.get().isSoloSession()
                    || state.activePlayerRole.equals(sessionMgr.getMyRole())
                    || opponentHasLeft;
            myTurn.setValue(turn);
        });
    }

    public void updateAfterAttempt(int attempt, boolean opponentChance) {
        if (remoteState == null) return;

        remoteState.attempt = attempt;
        remoteState.opponentChance = opponentChance;

        sessionRepo.updateSkockoState(remoteState);
    }

    public void addPointsToActivePlayer(int points) {
        if (remoteState == null) return;

        String roleToCredit = opponentHasLeft ? sessionMgr.getMyRole() : remoteState.activePlayerRole;
        if (GameSessionManager.ROLE_PLAYER1.equals(roleToCredit)) {
            remoteState.player1Score += points;
        } else {
            remoteState.player2Score += points;
        }

        sessionRepo.updateSkockoState(remoteState);
    }

    public void switchPlayer() {
        if (remoteState == null) return;

        if (GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole)) {
            remoteState.activePlayerRole = GameSessionManager.ROLE_PLAYER2;
        } else {
            remoteState.activePlayerRole = GameSessionManager.ROLE_PLAYER1;
        }

        sessionRepo.updateSkockoState(remoteState);
    }

    public void finishRound() {
        if (remoteState == null) return;
        if ("ROUND_END".equals(remoteState.phase) || "GAME_OVER".equals(remoteState.phase)) return;

        remoteState.phase = "ROUND_END";
        remoteState.roundFinished = true;

        sessionRepo.updateSkockoState(remoteState);
    }

    public void startNextRound() {
        if (remoteState == null) return;

        boolean isSolo = GameSessionManager.get().isSoloSession();

        if (!isSolo && !opponentHasLeft && remoteState.round == 1) {
            remoteState.round = 2;
            remoteState.phase = "PLAYING";
            remoteState.activePlayerRole = GameSessionManager.ROLE_PLAYER2;
            remoteState.secret = generateSecretAsString();
            remoteState.attempt = 0;
            remoteState.opponentChance = false;
            remoteState.roundFinished = false;
            remoteState.rows = new java.util.ArrayList<>();
            remoteState.lastCorrectPlace = 0;
            remoteState.lastCorrectSymbol = 0;
            remoteState.currentGuess = "";
        } else {
            remoteState.phase = "GAME_OVER";
            remoteState.status = "finished";
        }

        sessionRepo.updateSkockoState(remoteState);
    }

    public String[] getSecretArray() {
        if (remoteState == null || remoteState.secret == null) {
            return new String[]{"", "", "", ""};
        }

        return remoteState.secret.split(",");
    }

    private String generateSecretAsString() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append(",");
            sb.append(symbols[random.nextInt(symbols.length)]);
        }

        return sb.toString();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listener != null) listener.remove();
        if (abandonListener != null) abandonListener.remove();
    }

    public void saveAttempt(String guess, int correctPlace, int correctSymbol, int nextAttempt) {
        if (remoteState == null) return;

        if (remoteState.rows == null) {
            remoteState.rows = new java.util.ArrayList<>();
        }

        remoteState.rows.add(guess + "|" + correctPlace + "|" + correctSymbol);
        remoteState.attempt = nextAttempt;
        remoteState.lastCorrectPlace = correctPlace;
        remoteState.lastCorrectSymbol = correctSymbol;

        sessionRepo.updateSkockoState(remoteState);
    }

    public void startOpponentChance() {
        if (remoteState == null) return;
        // U solo modu nema protivnika — dodatni pokušaj se ne daje
        if (GameSessionManager.get().isSoloSession()) return;
        if (opponentHasLeft) {
            finishRound();
            return;
        }

        remoteState.opponentChance = true;

        if (GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole)) {
            remoteState.activePlayerRole = GameSessionManager.ROLE_PLAYER2;
        } else {
            remoteState.activePlayerRole = GameSessionManager.ROLE_PLAYER1;
        }

        sessionRepo.updateSkockoState(remoteState);
    }
}
