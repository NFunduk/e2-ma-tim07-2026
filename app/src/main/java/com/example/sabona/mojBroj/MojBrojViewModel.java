package com.example.sabona.mojBroj;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.game.GameSessionManager;
import com.example.sabona.game.GameSessionRepository;
import com.example.sabona.game.MojBrojGameState;
import com.example.sabona.repository.StatsRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Random;

/**
 * ViewModel za Moj Broj.
 *
 * Konzistentno s Ko Zna Zna:
 *  - activePlayerRole = "player1"/"player2" umesto UID-a
 *  - Host kreira sesiju, guest se pridružuje i menja fazu WAITING_P2 → IDLE
 *  - Isti GameSessionManager/Repository kao Korak
 */
public class MojBrojViewModel extends ViewModel {

    public enum Phase {
        LOADING,
        WAITING_P2,     // čekamo guesta
        IDLE,           // čekamo STOP za traženi broj
        REVEAL_TARGET,  // traženi broj otkriven, čekamo STOP za ponuđene
        PLAYING,        // OBA igrača unose izraz (60s)
        ROUND_END,      // runda gotova
        GAME_OVER
    }

    // ── LiveData ─────────────────────────────────────────────────────
    private final MutableLiveData<Phase>   phase          = new MutableLiveData<>(Phase.LOADING);
    private final MutableLiveData<String>  infoText       = new MutableLiveData<>();
    private final MutableLiveData<Integer> targetNumber   = new MutableLiveData<>(0);
    private final MutableLiveData<int[]>   offeredNumbers = new MutableLiveData<>();
    private final MutableLiveData<String>  roundLabel     = new MutableLiveData<>();
    private final MutableLiveData<int[]>   finalScores    = new MutableLiveData<>();
    private final MutableLiveData<Integer> liveExprResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isMyTurn       = new MutableLiveData<>(false);
    private final MutableLiveData<String>  roundSummary   = new MutableLiveData<>();
    private final MutableLiveData<Boolean> iDoneLocal     = new MutableLiveData<>(false);

    // ── Lokalno stanje ────────────────────────────────────────────────
    private MojBrojGameState remoteState    = new MojBrojGameState();
    private boolean          gameInitialized = false;

    private final GameSessionRepository sessionRepo = new GameSessionRepository();
    private final GameSessionManager    sessionMgr  = GameSessionManager.get();
    private final StatsRepository       statsRepo   = new StatsRepository();
    private ListenerRegistration        firestoreListener;

    private final Random rng = new Random();

    // ── Getteri ───────────────────────────────────────────────────────
    public LiveData<Phase>   getPhase()          { return phase; }
    public LiveData<String>  getInfoText()        { return infoText; }
    public LiveData<Integer> getTargetNumber()    { return targetNumber; }
    public LiveData<int[]>   getOfferedNumbers()  { return offeredNumbers; }
    public LiveData<String>  getRoundLabel()      { return roundLabel; }
    public LiveData<int[]>   getFinalScores()     { return finalScores; }
    public LiveData<Integer> getLiveExprResult()  { return liveExprResult; }
    public LiveData<Boolean> getIsMyTurn()        { return isMyTurn; }
    public LiveData<String>  getRoundSummary()    { return roundSummary; }
    public LiveData<Boolean> getIDoneLocal()      { return iDoneLocal; }

    public int getActivePlayerNumber() {
        return GameSessionManager.ROLE_PLAYER1.equals(remoteState.activePlayerRole) ? 1 : 2;
    }

    // ── Init ──────────────────────────────────────────────────────────

    public void init() {
        phase.setValue(Phase.LOADING);

        if (sessionMgr.isPlayer1() && !gameInitialized) {
            gameInitialized = true;
            setupAsHost();
        } else if (!sessionMgr.isPlayer1()) {
            setupAsGuest();
        } else {
            startListening();
        }
    }

    private void setupAsHost() {
        MojBrojGameState initState = new MojBrojGameState();
        initState.status            = "playing";
        initState.round             = 1;
        initState.activePlayerRole  = GameSessionManager.ROLE_PLAYER1;
        initState.phase             = "WAITING_P2";
        initState.targetNumber      = 0;
        initState.offeredNumbers    = "";
        initState.player1Score      = 0;
        initState.player2Score      = 0;
        initState.player1RoundResult = -1;
        initState.player2RoundResult = -1;
        initState.player1Done       = false;
        initState.player2Done       = false;
        initState.hostUid           = sessionMgr.getMyUid();
        sessionRepo.initMojBrojState(initState);
        startListening();
    }

    private void setupAsGuest() {
        // Guest čita sesiju i menja WAITING_P2 → IDLE (kao KoZnaZna guest menja na "question")
        sessionRepo.getKorakOnce((snap, e) -> {
            // Ne postoji getOnce za MojBroj — dodaj u repo, ili radi direktno
            // Ovde koristimo listenMojBroj jednokratno
        });

        // Alternativno: slušaj i pri prvom snapshot-u promeni fazu
        firestoreListener = sessionRepo.listenMojBroj((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;
            MojBrojGameState state = snapshot.toObject(MojBrojGameState.class);
            if (state == null) return;

            if ("WAITING_P2".equals(state.phase) && !gameInitialized) {
                gameInitialized = true;
                MojBrojGameState newState = copyState(state);
                newState.phase = "IDLE";
                sessionRepo.updateMojBrojState(newState);
            }

            remoteState = state;
            applyRemoteState(state);
        });
    }

    private void startListening() {
        if (firestoreListener != null) return; // ne registruj duplo
        firestoreListener = sessionRepo.listenMojBroj((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;
            MojBrojGameState state = snapshot.toObject(MojBrojGameState.class);
            if (state == null) return;
            remoteState = state;
            applyRemoteState(state);
        });
    }

    // ── Primeni stanje ────────────────────────────────────────────────

    private void applyRemoteState(MojBrojGameState state) {
        boolean myTurn = isMyActiveRole(state.activePlayerRole);
        isMyTurn.postValue(myTurn);

        int activeNum = GameSessionManager.ROLE_PLAYER1.equals(state.activePlayerRole) ? 1 : 2;
        roundLabel.postValue("Runda " + state.round + "/2  —  Igrač " + activeNum + " stopira");

        if (state.offeredNumbers != null && !state.offeredNumbers.isEmpty()) {
            offeredNumbers.postValue(parseNumbers(state.offeredNumbers));
        }
        if (state.targetNumber > 0) targetNumber.postValue(state.targetNumber);

        switch (state.phase) {
            case "WAITING_P2":
                iDoneLocal.postValue(false);
                phase.postValue(Phase.WAITING_P2);
                infoText.postValue("Čekam Igrača 2...\nKod: " + sessionMgr.getSessionId());
                break;

            case "IDLE":
                iDoneLocal.postValue(false);
                phase.postValue(Phase.IDLE);
                infoText.postValue(myTurn
                        ? "Tvoj red! Pritisni STOP da vidiš traženi broj."
                        : "Igrač " + activeNum + " pritiska STOP za traženi broj...");
                break;

            case "REVEAL_TARGET":
                phase.postValue(Phase.REVEAL_TARGET);
                infoText.postValue(myTurn
                        ? "Traženi: " + state.targetNumber + " — pritisni STOP za ponuđene."
                        : "Traženi: " + state.targetNumber + " — čekamo igrača " + activeNum + "...");
                break;

            case "PLAYING":
                phase.postValue(Phase.PLAYING);
                boolean myDone = sessionMgr.isPlayer1() ? state.player1Done : state.player2Done;
                boolean opDone = sessionMgr.isPlayer1() ? state.player2Done : state.player1Done;
                if (myDone) {
                    infoText.postValue(opDone ? "Oba igrača su završila..." : "Predao/la si. Čekamo protivnika...");
                } else {
                    infoText.postValue("Napravi " + state.targetNumber
                            + " od ponuđenih! (" + (opDone ? "Protivnik završio" : "Oba igraju") + ")");
                }
                break;

            case "ROUND_END":
                phase.postValue(Phase.ROUND_END);
                String summary = buildRoundSummary(state);
                roundSummary.postValue(summary);
                infoText.postValue(summary);
                break;

            case "GAME_OVER":
                phase.postValue(Phase.GAME_OVER);
                finalScores.postValue(new int[]{state.player1Score, state.player2Score});
                // Spremi statistiku (Student 2 funkcionalnost)
                if (sessionMgr.isPlayer1()) {
                    boolean foundExact = (state.player1RoundResult == state.targetNumber
                            && state.player1RoundResult != -1);
                    statsRepo.saveMojBrojResult(state.player1Score, foundExact);
                    boolean iWon = state.player1Score > state.player2Score;
                    statsRepo.incrementGamesPlayed(iWon);
                } else {
                    boolean foundExact = (state.player2RoundResult == state.targetNumber
                            && state.player2RoundResult != -1);
                    statsRepo.saveMojBrojResult(state.player2Score, foundExact);
                    boolean iWon = state.player2Score > state.player1Score;
                    statsRepo.incrementGamesPlayed(iWon);
                }
                break;
        }
    }

    private boolean isMyActiveRole(String role) {
        return sessionMgr.isPlayer1()
                ? GameSessionManager.ROLE_PLAYER1.equals(role)
                : GameSessionManager.ROLE_PLAYER2.equals(role);
    }

    private String buildRoundSummary(MojBrojGameState state) {
        String p1r = state.player1RoundResult == -1 ? "nije uneo" : String.valueOf(state.player1RoundResult);
        String p2r = state.player2RoundResult == -1 ? "nije uneo" : String.valueOf(state.player2RoundResult);
        return String.format("Traženi: %d | P1: %s | P2: %s | Ukupno: %d – %d",
                state.targetNumber, p1r, p2r, state.player1Score, state.player2Score);
    }

    // ── Akcije igrača ─────────────────────────────────────────────────

    public void onStop() {
        if (!isMyActiveRole(remoteState.activePlayerRole)) return;

        sessionRepo.runMojBrojTransaction(current -> {
            if (!isMyActiveRole(current.activePlayerRole)) return null;

            MojBrojGameState newState = copyState(current);
            if ("IDLE".equals(current.phase)) {
                newState.targetNumber = 100 + rng.nextInt(900);
                newState.phase = "REVEAL_TARGET";
                return newState;
            } else if ("REVEAL_TARGET".equals(current.phase)) {
                newState.offeredNumbers      = generateOfferedNumbers();
                newState.phase               = "PLAYING";
                newState.player1Done         = false;
                newState.player2Done         = false;
                newState.player1RoundResult  = -1;
                newState.player2RoundResult  = -1;
                return newState;
            }
            return null;
        });
    }

    public void onTargetAutoReveal()  { if ("IDLE".equals(remoteState.phase)) onStop(); }
    public void onNumbersAutoReveal() { if ("REVEAL_TARGET".equals(remoteState.phase)) onStop(); }

    public void onExpressionChanged(String expr) {
        if (expr == null || expr.trim().isEmpty()) { liveExprResult.setValue(null); return; }
        try {
            ExpressionEvaluator ev = new ExpressionEvaluator(expr);
            double val = ev.evaluate();
            if (val == Math.floor(val) && val > 0) liveExprResult.setValue((int) val);
            else liveExprResult.setValue(null);
        } catch (ExpressionEvaluator.EvalException e) {
            liveExprResult.setValue(null);
        }
    }

    public int submitExpression(String expr) {
        if (!"PLAYING".equals(remoteState.phase)) return -1;
        boolean alreadyDone = sessionMgr.isPlayer1() ? remoteState.player1Done : remoteState.player2Done;
        if (alreadyDone) return -1;
        int result = evaluateExpr(expr);
        if (result == -1) return -1;
        recordMyResult(result);
        return result;
    }

    public void onRoundTimerFinished(String currentExpr) {
        if (!"PLAYING".equals(remoteState.phase)) return;
        boolean alreadyDone = sessionMgr.isPlayer1() ? remoteState.player1Done : remoteState.player2Done;
        if (alreadyDone) return;
        int result = evaluateExpr(currentExpr);
        recordMyResult(result);
    }

    private void recordMyResult(int myResult) {
        boolean isP1 = sessionMgr.isPlayer1();

        sessionRepo.runMojBrojTransaction(current -> {
            if (!"PLAYING".equals(current.phase)) return null;

            boolean alreadyDone = isP1 ? current.player1Done : current.player2Done;
            if (alreadyDone) return null;

            MojBrojGameState newState = copyState(current);
            if (isP1) {
                newState.player1RoundResult = myResult;
                newState.player1Done = true;
            } else {
                newState.player2RoundResult = myResult;
                newState.player2Done = true;
            }

            boolean bothDone = newState.player1Done && newState.player2Done;
            if (bothDone) {
                applyScoring(newState);
                newState.phase = "ROUND_END";
            }
            return newState;
        });

        iDoneLocal.postValue(true);
    }

    /** Bodovanje po specifikaciji */
    private void applyScoring(MojBrojGameState s) {
        int target = s.targetNumber;
        int r1 = s.player1RoundResult;
        int r2 = s.player2RoundResult;

        boolean p1Hit = (r1 == target);
        boolean p2Hit = (r2 == target);

        if (p1Hit && p2Hit) { s.player1Score += 10; s.player2Score += 10; }
        else if (p1Hit)     { s.player1Score += 10; }
        else if (p2Hit)     { s.player2Score += 10; }
        else {
            if (r1 == -1 && r2 == -1) return;
            if (r1 == -1) { s.player2Score += 5; return; }
            if (r2 == -1) { s.player1Score += 5; return; }

            int d1 = Math.abs(r1 - target);
            int d2 = Math.abs(r2 - target);

            if (d1 < d2) {
                s.player1Score += 5;
            } else if (d2 < d1) {
                s.player2Score += 5;
            } else {
                // Isti razlika → čija je runda dobija 5
                if (GameSessionManager.ROLE_PLAYER1.equals(s.activePlayerRole)) s.player1Score += 5;
                else s.player2Score += 5;
            }
        }
    }

    public void onRoundEndCountdownFinished() {
        if (!"ROUND_END".equals(remoteState.phase)) return;
        if (!sessionMgr.isPlayer1()) return; // samo host napreduje

        sessionRepo.runMojBrojTransaction(current -> {
            if (!"ROUND_END".equals(current.phase)) return null;

            MojBrojGameState newState = copyState(current);
            if (current.round == 1) {
                newState.round              = 2;
                newState.activePlayerRole   = GameSessionManager.ROLE_PLAYER2;
                newState.phase              = "IDLE";
                newState.targetNumber       = 0;
                newState.offeredNumbers     = "";
                newState.player1RoundResult = -1;
                newState.player2RoundResult = -1;
                newState.player1Done        = false;
                newState.player2Done        = false;
            } else {
                newState.phase  = "GAME_OVER";
                newState.status = "finished";
            }
            return newState;
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private int evaluateExpr(String expr) {
        if (expr == null || expr.trim().isEmpty()) return -1;
        try {
            ExpressionEvaluator ev = new ExpressionEvaluator(expr);
            double val = ev.evaluate();
            if (val == Math.floor(val) && val > 0) return (int) val;
        } catch (ExpressionEvaluator.EvalException ignored) {}
        return -1;
    }

    private String generateOfferedNumbers() {
        int[] nums = new int[6];
        nums[0] = 1 + rng.nextInt(9);
        nums[1] = 1 + rng.nextInt(9);
        nums[2] = 1 + rng.nextInt(9);
        nums[3] = 1 + rng.nextInt(9);
        int[] medium = {10, 15, 20};
        nums[4] = medium[rng.nextInt(3)];
        int[] large = {25, 50, 75, 100};
        nums[5] = large[rng.nextInt(4)];
        for (int i = 5; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = nums[i]; nums[i] = nums[j]; nums[j] = tmp;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) { sb.append(nums[i]); if (i < 5) sb.append(","); }
        return sb.toString();
    }

    private int[] parseNumbers(String s) {
        String[] parts = s.split(",");
        int[] result = new int[6];
        for (int i = 0; i < Math.min(parts.length, 6); i++) {
            try { result[i] = Integer.parseInt(parts[i].trim()); }
            catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private MojBrojGameState copyState(MojBrojGameState src) {
        MojBrojGameState copy = new MojBrojGameState();
        copy.status              = src.status;
        copy.round               = src.round;
        copy.activePlayerRole    = src.activePlayerRole;
        copy.phase               = src.phase;
        copy.targetNumber        = src.targetNumber;
        copy.offeredNumbers      = src.offeredNumbers;
        copy.player1Score        = src.player1Score;
        copy.player2Score        = src.player2Score;
        copy.player1RoundResult  = src.player1RoundResult;
        copy.player2RoundResult  = src.player2RoundResult;
        copy.player1Done         = src.player1Done;
        copy.player2Done         = src.player2Done;
        copy.hostUid             = src.hostUid;
        return copy;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (firestoreListener != null) firestoreListener.remove();
    }
}