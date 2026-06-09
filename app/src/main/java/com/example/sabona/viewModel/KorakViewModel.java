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

import java.util.Collections;
import java.util.List;

public class KorakViewModel extends ViewModel {

    public enum Phase { LOADING, WAITING, MAIN, BONUS, ROUND_END, GAME_OVER }

    // ── LiveData za Fragment ──────────────────────────────────────────
    private final MutableLiveData<Phase>   phase         = new MutableLiveData<>(Phase.LOADING);
    private final MutableLiveData<String>  infoText      = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentPoints = new MutableLiveData<>(20);
    private final MutableLiveData<Integer> stepsRevealed = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> error         = new MutableLiveData<>();
    private final MutableLiveData<int[]>   finalScores   = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isMyTurn      = new MutableLiveData<>(false);
    private final MutableLiveData<String>  answerFeedback = new MutableLiveData<>();

    // ── Lokalno stanje ────────────────────────────────────────────────
    private List<KorakGame> games;
    private KorakGameState  remoteState = new KorakGameState();
    private boolean         iAmPlayer1;
    private boolean         gameInitialized = false;

    private final KorakRepository     korakRepo = new KorakRepository();
    private final GameSessionRepository sessionRepo = new GameSessionRepository();
    private final GameSessionManager  sessionMgr  = GameSessionManager.get();

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
        if (games == null || remoteState.gameIndex >= games.size()) return null;
        return games.get(remoteState.gameIndex);
    }

    public int getActivePlayerNumber() {
        return remoteState.activePlayerUid.equals(GameSessionManager.UID_PLAYER1) ? 1 : 2;
    }

    public int getRound() { return remoteState.round; }

    // ── Init ──────────────────────────────────────────────────────────

    public void init() {
        iAmPlayer1 = sessionMgr.isPlayer1();
        phase.setValue(Phase.LOADING);

        korakRepo.getGames(new KorakRepository.Callback() {
            @Override
            public void onSuccess(List<KorakGame> result) {
                Collections.shuffle(result);
                games = result.subList(0, Math.min(2, result.size()));

                // Player1 inicijalizuje stanje, player2 samo sluša
                if (iAmPlayer1 && !gameInitialized) {
                    gameInitialized = true;
                    KorakGameState initState = new KorakGameState();
                    initState.status       = "playing";
                    initState.round        = 1;
                    initState.activePlayerUid = GameSessionManager.UID_PLAYER1;
                    initState.phase        = "MAIN";
                    initState.stepsRevealed = 1;
                    initState.gameIndex    = 0;
                    initState.player1Score = 0;
                    initState.player2Score = 0;
                    sessionRepo.initKorakState(initState);
                }

                startListening();
            }

            @Override
            public void onError(Exception e) {
                error.postValue(true);
            }
        });
    }

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
        boolean myTurn = state.activePlayerUid.equals(sessionMgr.getMyUid());
        isMyTurn.postValue(myTurn);

        int activePlayer = state.activePlayerUid.equals(GameSessionManager.UID_PLAYER1) ? 1 : 2;

        stepsRevealed.postValue(state.stepsRevealed);

        int pts = Math.max(20 - (state.stepsRevealed - 1) * 2, 0);
        currentPoints.postValue("BONUS".equals(state.phase) ? 5 : pts);

        switch (state.phase) {
            case "MAIN":
                phase.postValue(Phase.MAIN);
                if (myTurn) {
                    infoText.postValue("Tvoj red! Runda " + state.round + "/2 — pogodi pojam.");
                } else {
                    infoText.postValue("Igrač " + activePlayer + " igra. Čekaj...");
                }
                break;

            case "BONUS":
                phase.postValue(Phase.BONUS);
                int opponentNum = activePlayer == 1 ? 2 : 1;
                boolean iAmOpponent = sessionMgr.getMyPlayerNumber() == opponentNum;
                if (iAmOpponent) {
                    infoText.postValue("Protivnik nije pogodio! Tvoj bonus — 5 bodova ako pogodis!");
                } else {
                    infoText.postValue("Nisi pogodio. Protivnik pokušava bonus...");
                }
                break;

            case "ROUND_END":
                phase.postValue(Phase.ROUND_END);
                if (state.lastAnswerResult != null) {
                    String feedback = buildFeedback(state);
                    answerFeedback.postValue(feedback);
                    infoText.postValue(feedback);
                }
                break;

            case "GAME_OVER":
                phase.postValue(Phase.GAME_OVER);
                finalScores.postValue(new int[]{state.player1Score, state.player2Score});
                break;
        }
    }

    private String buildFeedback(KorakGameState state) {
        if ("correct".equals(state.lastAnswerResult)) {
            return "Igrač " + state.lastAnswerPlayer + " tačno! +" + state.lastPointsAwarded + " bod.";
        } else {
            return "Niko nije pogodio. Rešenje: " +
                    (currentGame() != null ? currentGame().answer : "");
        }
    }

    // ── Akcije igrača ─────────────────────────────────────────────────

    /**
     * Pokušaj odgovora. Samo aktivni igrač (ili protivnik u BONUS fazi) sme da pozove.
     */
    public boolean submitAnswer(String guess) {
        if (games == null || currentGame() == null) return false;
        if (!guess.equalsIgnoreCase(currentGame().answer)) return false;

        KorakGameState newState = copyState(remoteState);

        boolean inBonus = "BONUS".equals(remoteState.phase);

        if (!inBonus) {
            // Aktivan igrač pogodio
            int pts = Math.max(20 - (remoteState.stepsRevealed - 1) * 2, 0);
            if (remoteState.activePlayerUid.equals(GameSessionManager.UID_PLAYER1)) {
                newState.player1Score += pts;
            } else {
                newState.player2Score += pts;
            }
            newState.lastPointsAwarded = pts;
            newState.lastAnswerPlayer  = getActivePlayerNumber();
        } else {
            // Protivnik pogodio u bonus fazi — dobija 5 bodova
            // Protivnik = suprotno od activePlayer
            boolean activeIsP1 = remoteState.activePlayerUid.equals(GameSessionManager.UID_PLAYER1);
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

    /** Tajmer za glavnih 70s istekao — poziva Fragment */
    public void onMainTimerFinished() {
        if (!"MAIN".equals(remoteState.phase)) return;
        // Samo aktivni igrač šalje update (jer je samo on imao tajmer)
        if (!remoteState.activePlayerUid.equals(sessionMgr.getMyUid())) return;

        KorakGameState newState = copyState(remoteState);
        newState.phase = "BONUS";
        sessionRepo.updateKorakState(newState);
    }

    /** Tajmer za bonus 10s istekao */
    public void onBonusTimerFinished() {
        if (!"BONUS".equals(remoteState.phase)) return;
        // Protivnik ima tajmer — ko je suprotno od activePlayer
        boolean activeIsP1 = remoteState.activePlayerUid.equals(GameSessionManager.UID_PLAYER1);
        boolean iAmOpponent = iAmPlayer1 != activeIsP1;
        if (!iAmOpponent) return;

        KorakGameState newState = copyState(remoteState);
        newState.lastAnswerResult = "wrong";
        newState.lastAnswerPlayer = 0;
        newState.lastPointsAwarded = 0;
        newState.phase = "ROUND_END";
        sessionRepo.updateKorakState(newState);
    }

    /** Fragment poziva posle 3s pauze na kraju runde */
    public void onRoundEndCountdownFinished() {
        if (!"ROUND_END".equals(remoteState.phase)) return;
        // Samo player1 pokreće sledeću rundu (da ne bi oba pisala)
        if (!iAmPlayer1) return;

        if (remoteState.round == 1) {
            KorakGameState newState = copyState(remoteState);
            newState.round = 2;
            newState.activePlayerUid = GameSessionManager.UID_PLAYER2;
            newState.phase = "MAIN";
            newState.stepsRevealed = 1;
            newState.gameIndex = 1;
            newState.lastAnswerResult = null;
            newState.player1Answer = null;
            newState.player2Answer = null;
            sessionRepo.updateKorakState(newState);
        } else {
            KorakGameState newState = copyState(remoteState);
            newState.phase = "GAME_OVER";
            newState.status = "finished";
            sessionRepo.updateKorakState(newState);
        }
    }

    /** Svaki telefon lokalno otkriva korake prema tajmeru */
    public void revealNextStep() {
        // Ovo je samo lokalni prikaz — pravi update šalje samo aktivni igrač
        if (!remoteState.activePlayerUid.equals(sessionMgr.getMyUid())) return;
        if (remoteState.stepsRevealed >= 7) return;
        if (!"MAIN".equals(remoteState.phase)) return;

        KorakGameState newState = copyState(remoteState);
        newState.stepsRevealed = remoteState.stepsRevealed + 1;
        sessionRepo.updateKorakState(newState);
    }

    // ── Cleanup ───────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        if (firestoreListener != null) firestoreListener.remove();
    }

    // ── Helper ────────────────────────────────────────────────────────

    private KorakGameState copyState(KorakGameState src) {
        KorakGameState copy = new KorakGameState();
        copy.status           = src.status;
        copy.round            = src.round;
        copy.activePlayerUid  = src.activePlayerUid;
        copy.phase            = src.phase;
        copy.stepsRevealed    = src.stepsRevealed;
        copy.gameIndex        = src.gameIndex;
        copy.player1Score     = src.player1Score;
        copy.player2Score     = src.player2Score;
        copy.player1Answer    = src.player1Answer;
        copy.player2Answer    = src.player2Answer;
        copy.lastAnswerResult = src.lastAnswerResult;
        copy.lastAnswerPlayer = src.lastAnswerPlayer;
        copy.lastPointsAwarded = src.lastPointsAwarded;
        return copy;
    }
}