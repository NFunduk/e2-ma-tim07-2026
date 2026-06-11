package com.example.sabona.viewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.game.GameSessionManager;
import com.example.sabona.game.GameSessionRepository;
import com.example.sabona.game.KorakGameState;
import com.example.sabona.model.KorakGame;
import com.example.sabona.repository.KorakRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel za Korak po korak.
 *
 * Konzistentno s Ko Zna Zna:
 *  - Host kreira sesiju i upisuje game0Id/game1Id u Firestore.
 *  - Guest čita iste ID-ove i mapira na lokalno-učitane igre.
 *  - activePlayerRole = "player1"/"player2" umesto UID-a.
 */
public class KorakViewModel extends ViewModel {

    public enum Phase { LOADING, WAITING_P2, MAIN, BONUS, ROUND_END, GAME_OVER }

    // ── LiveData ─────────────────────────────────────────────────────
    private final MutableLiveData<Phase>   phase          = new MutableLiveData<>(Phase.LOADING);
    private final MutableLiveData<String>  infoText       = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentPoints  = new MutableLiveData<>(20);
    private final MutableLiveData<Integer> stepsRevealed  = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> error          = new MutableLiveData<>();
    private final MutableLiveData<int[]>   finalScores    = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isMyTurn       = new MutableLiveData<>(false);
    private final MutableLiveData<String>  answerFeedback = new MutableLiveData<>();

    // ── Lokalno stanje ────────────────────────────────────────────────
    private List<KorakGame>        allGames;       // sve igre učitane iz Firestorea
    private Map<String, KorakGame> gameIdMap;      // docId → KorakGame
    private List<KorakGame>        orderedGames;   // 2 igre u redosledu koji je host odredio

    private KorakGameState remoteState   = new KorakGameState();
    private boolean        gameInitialized = false;

    private final KorakRepository       korakRepo   = new KorakRepository();
    private final GameSessionRepository sessionRepo = new GameSessionRepository();
    private final GameSessionManager    sessionMgr  = GameSessionManager.get();

    private ListenerRegistration firestoreListener;

    // ── Getteri ───────────────────────────────────────────────────────
    public LiveData<Phase>   getPhase()          { return phase; }
    public LiveData<String>  getInfoText()        { return infoText; }
    public LiveData<Integer> getCurrentPoints()   { return currentPoints; }
    public LiveData<Integer> getStepsRevealed()   { return stepsRevealed; }
    public LiveData<Boolean> getError()           { return error; }
    public LiveData<int[]>   getFinalScores()     { return finalScores; }
    public LiveData<Boolean> getIsMyTurn()        { return isMyTurn; }
    public LiveData<String>  getAnswerFeedback()  { return answerFeedback; }

    public KorakGame currentGame() {
        if (orderedGames == null || remoteState.gameIndex >= orderedGames.size()) return null;
        return orderedGames.get(remoteState.gameIndex);
    }

    public int getActivePlayerNumber() {
        return GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole) ? 1 : 2;
    }

    public int getRound() { return remoteState.round; }

    // ── Init (poziva Fragment, kao u KoZnaZna) ────────────────────────

    public void init() {
        phase.setValue(Phase.LOADING);

        korakRepo.getGamesWithIds(new KorakRepository.CallbackWithIds() {
            @Override
            public void onSuccess(List<KorakGame> games) {
                allGames   = games;
                gameIdMap  = new HashMap<>();
                for (KorakGame g : games) {
                    if (g.docId != null) gameIdMap.put(g.docId, g);
                }

                if (sessionMgr.isPlayer1() && !gameInitialized) {
                    gameInitialized = true;
                    setupAsHost();
                } else if (!sessionMgr.isPlayer1()) {
                    setupAsGuest();
                }
            }

            @Override
            public void onError(Exception e) {
                error.postValue(true);
            }
        });
    }

    // ── Host inicijalizacija ──────────────────────────────────────────

    private void setupAsHost() {
        // Host bira 2 igre i upisuje njihove ID-ove (kao KoZnaZna sa questionIds)
        List<KorakGame> shuffled = new ArrayList<>(allGames);
        Collections.shuffle(shuffled);

        KorakGame g0 = shuffled.get(0);
        KorakGame g1 = shuffled.get(1);
        orderedGames = new ArrayList<>();
        orderedGames.add(g0);
        orderedGames.add(g1);

        KorakGameState initState = new KorakGameState();
        initState.status          = "playing";
        initState.round           = 1;
        initState.activePlayerRole = GameSessionManager.ROLE_PLAYER1;
        initState.phase           = "WAITING_P2";
        initState.stepsRevealed   = 1;
        initState.gameIndex       = 0;
        initState.game0Id         = g0.docId;
        initState.game1Id         = g1.docId;
        initState.player1Score    = 0;
        initState.player2Score    = 0;
        initState.hostUid         = sessionMgr.getMyUid();

        sessionRepo.initKorakState(initState);
        startListening();
    }

    // ── Guest inicijalizacija ─────────────────────────────────────────

    private void setupAsGuest() {
        // Guest čita sesiju da dobije game0Id/game1Id — kao KoZnaZna
        sessionRepo.getKorakOnce((snap, e) -> {
            if (e != null || snap == null || !snap.exists()) {
                error.postValue(true);
                return;
            }

            KorakGameState hostState = snap.toObject(KorakGameState.class);
            if (hostState == null) { error.postValue(true); return; }

            // Mapiraj igre prema ID-ovima koje je host upisao
            KorakGame g0 = gameIdMap.get(hostState.game0Id);
            KorakGame g1 = gameIdMap.get(hostState.game1Id);
            if (g0 == null || g1 == null) { error.postValue(true); return; }

            orderedGames = new ArrayList<>();
            orderedGames.add(g0);
            orderedGames.add(g1);

            // Prebaci fazu u MAIN (guest potvrđuje da je spreman)
            KorakGameState newState = copyState(hostState);
            newState.phase = "MAIN";
            sessionRepo.updateKorakState(newState);

            startListening();
        });
    }

    // ── Firestore listener ────────────────────────────────────────────

    private void startListening() {
        firestoreListener = sessionRepo.listenKorak((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;

            KorakGameState state = snapshot.toObject(KorakGameState.class);
            if (state == null) return;

            remoteState = state;
            applyRemoteState(state);
        });
    }

    private void applyRemoteState(KorakGameState state) {
        boolean myTurn = isMyActiveRole(state.activePlayerRole);
        isMyTurn.postValue(myTurn);

        stepsRevealed.postValue(state.stepsRevealed);

        int pts = Math.max(20 - (state.stepsRevealed - 1) * 2, 0);
        currentPoints.postValue("BONUS".equals(state.phase) ? 5 : pts);

        int activeNum = GameSessionManager.ROLE_PLAYER1.equals(state.activePlayerRole) ? 1 : 2;

        switch (state.phase) {
            case "WAITING_P2":
                phase.postValue(Phase.WAITING_P2);
                infoText.postValue("Čekam Igrača 2...");
                break;

            case "MAIN":
                phase.postValue(Phase.MAIN);
                infoText.postValue(myTurn
                        ? "Tvoj red! Pogodi pojam."
                        : "Igrač " + activeNum + " igra. Čekaj...");
                break;

            case "BONUS":
                phase.postValue(Phase.BONUS);
                boolean iAmOpponent = !myTurn;
                infoText.postValue(iAmOpponent
                        ? "Protivnik nije pogodio! Tvoj bonus — 5 bodova ako pogodis!"
                        : "Nisi pogodio. Protivnik pokušava bonus...");
                break;

            case "ROUND_END":
                phase.postValue(Phase.ROUND_END);
                String feedback = buildFeedback(state);
                answerFeedback.postValue(feedback);
                infoText.postValue(feedback);
                break;

            case "GAME_OVER":
                phase.postValue(Phase.GAME_OVER);
                finalScores.postValue(new int[]{state.player1Score, state.player2Score});
                break;
        }
    }

    private boolean isMyActiveRole(String role) {
        return sessionMgr.isPlayer1()
                ? GameSessionManager.ROLE_PLAYER1.equals(role)
                : GameSessionManager.ROLE_PLAYER2.equals(role);
    }

    private String buildFeedback(KorakGameState state) {
        if ("correct".equals(state.lastAnswerResult)) {
            return "Igrač " + state.lastAnswerPlayer + " tačno! +" + state.lastPointsAwarded + " bod.";
        }
        return "Niko nije pogodio. Rešenje: "
                + (currentGame() != null ? currentGame().answer : "");
    }

    // ── Akcije igrača ─────────────────────────────────────────────────

    public boolean submitAnswer(String guess) {
        if (currentGame() == null) return false;
        if (!guess.equalsIgnoreCase(currentGame().answer)) return false;

        KorakGameState newState = copyState(remoteState);
        boolean inBonus = "BONUS".equals(remoteState.phase);

        if (!inBonus) {
            int pts = Math.max(20 - (remoteState.stepsRevealed - 1) * 2, 0);
            if (GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole)) {
                newState.player1Score += pts;
            } else {
                newState.player2Score += pts;
            }
            newState.lastPointsAwarded = pts;
            newState.lastAnswerPlayer  = getActivePlayerNumber();
        } else {
            // Protivnik u bonus fazi
            boolean activeIsP1 = GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole);
            if (activeIsP1) {
                newState.player2Score += 5;
            } else {
                newState.player1Score += 5;
            }
            newState.lastPointsAwarded = 5;
            newState.lastAnswerPlayer  = sessionMgr.getMyPlayerNumber();
        }

        newState.lastAnswerResult = "correct";
        newState.phase = "ROUND_END";
        sessionRepo.updateKorakState(newState);
        return true;
    }

    public void onMainTimerFinished() {
        if (!"MAIN".equals(remoteState.phase)) return;
        if (!isMyActiveRole(remoteState.activePlayerRole)) return;

        KorakGameState newState = copyState(remoteState);
        newState.phase = "BONUS";
        sessionRepo.updateKorakState(newState);
    }

    public void onBonusTimerFinished() {
        if (!"BONUS".equals(remoteState.phase)) return;
        boolean activeIsP1 = GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole);
        boolean iAmOpponent = sessionMgr.isPlayer1() != activeIsP1;
        if (!iAmOpponent) return;

        KorakGameState newState = copyState(remoteState);
        newState.lastAnswerResult  = "wrong";
        newState.lastAnswerPlayer  = 0;
        newState.lastPointsAwarded = 0;
        newState.phase = "ROUND_END";
        sessionRepo.updateKorakState(newState);
    }

    public void onRoundEndCountdownFinished() {
        if (!"ROUND_END".equals(remoteState.phase)) return;
        if (!sessionMgr.isPlayer1()) return; // samo host napreduje

        if (remoteState.round == 1) {
            KorakGameState newState = copyState(remoteState);
            newState.round            = 2;
            newState.activePlayerRole = GameSessionManager.ROLE_PLAYER2;
            newState.phase            = "MAIN";
            newState.stepsRevealed    = 1;
            newState.gameIndex        = 1;
            newState.lastAnswerResult = null;
            newState.player1Answer    = null;
            newState.player2Answer    = null;
            sessionRepo.updateKorakState(newState);
        } else {
            KorakGameState newState = copyState(remoteState);
            newState.phase  = "GAME_OVER";
            newState.status = "finished";
            sessionRepo.updateKorakState(newState);
        }
    }

    public void revealNextStep() {
        if (!isMyActiveRole(remoteState.activePlayerRole)) return;
        if (remoteState.stepsRevealed >= 7) return;
        if (!"MAIN".equals(remoteState.phase)) return;

        KorakGameState newState = copyState(remoteState);
        newState.stepsRevealed = remoteState.stepsRevealed + 1;
        sessionRepo.updateKorakState(newState);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (firestoreListener != null) firestoreListener.remove();
    }

    private KorakGameState copyState(KorakGameState src) {
        KorakGameState copy = new KorakGameState();
        copy.status            = src.status;
        copy.round             = src.round;
        copy.activePlayerRole  = src.activePlayerRole;
        copy.phase             = src.phase;
        copy.stepsRevealed     = src.stepsRevealed;
        copy.gameIndex         = src.gameIndex;
        copy.game0Id           = src.game0Id;
        copy.game1Id           = src.game1Id;
        copy.player1Score      = src.player1Score;
        copy.player2Score      = src.player2Score;
        copy.player1Answer     = src.player1Answer;
        copy.player2Answer     = src.player2Answer;
        copy.lastAnswerResult  = src.lastAnswerResult;
        copy.lastAnswerPlayer  = src.lastAnswerPlayer;
        copy.lastPointsAwarded = src.lastPointsAwarded;
        copy.hostUid           = src.hostUid;
        return copy;
    }

}