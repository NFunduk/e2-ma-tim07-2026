package com.example.sabona.mojBroj;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.game.GameSessionManager;
import com.example.sabona.game.GameSessionRepository;
import com.example.sabona.game.MojBrojGameState;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Random;

public class MojBrojViewModel extends ViewModel {

    public enum Phase {
        IDLE,           // čekamo STOP za traženi broj
        REVEAL_TARGET,  // traženi broj otkriven, čekamo STOP za ponuđene
        PLAYING,        // igrač unosi izraz
        ROUND_END,      // runda gotova
        GAME_OVER       // obe runde gotove
    }

    // ── LiveData ─────────────────────────────────────────────────────
    private final MutableLiveData<Phase>   phase          = new MutableLiveData<>(Phase.IDLE);
    private final MutableLiveData<String>  infoText       = new MutableLiveData<>();
    private final MutableLiveData<Integer> targetNumber   = new MutableLiveData<>(0);
    private final MutableLiveData<int[]>   offeredNumbers = new MutableLiveData<>();
    private final MutableLiveData<String>  roundLabel     = new MutableLiveData<>();
    private final MutableLiveData<int[]>   finalScores    = new MutableLiveData<>();
    private final MutableLiveData<Integer> liveExprResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isMyTurn       = new MutableLiveData<>(false);
    private final MutableLiveData<String>  roundSummary   = new MutableLiveData<>();

    // ── Lokalno stanje ────────────────────────────────────────────────
    private MojBrojGameState remoteState = new MojBrojGameState();
    private boolean iAmPlayer1;
    private boolean initialized = false;

    private final GameSessionRepository sessionRepo = new GameSessionRepository();
    private final GameSessionManager    sessionMgr  = GameSessionManager.get();
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

    public int getActivePlayer() {
        return remoteState.activePlayerUid.equals(GameSessionManager.UID_PLAYER1) ? 1 : 2;
    }

    // ── Init ──────────────────────────────────────────────────────────

    public void init() {
        iAmPlayer1 = sessionMgr.isPlayer1();

        // Player1 kreira početno stanje
        if (iAmPlayer1 && !initialized) {
            initialized = true;
            MojBrojGameState initState = new MojBrojGameState();
            initState.status          = "playing";
            initState.round           = 1;
            initState.activePlayerUid = GameSessionManager.UID_PLAYER1;
            initState.phase           = "IDLE";
            initState.targetNumber    = 0;
            initState.offeredNumbers  = "";
            initState.player1Score    = 0;
            initState.player2Score    = 0;
            initState.player1RoundResult = -1;
            initState.player2RoundResult = -1;
            sessionRepo.initMojBrojState(initState);
        }

        startListening();
    }

    private void startListening() {
        firestoreListener = sessionRepo.listenMojBroj((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;

            MojBrojGameState state = snapshot.toObject(MojBrojGameState.class);
            if (state == null) return;

            remoteState = state;
            applyRemoteState(state);
        });
    }

    private void applyRemoteState(MojBrojGameState state) {
        boolean myTurn = state.activePlayerUid.equals(sessionMgr.getMyUid());
        isMyTurn.postValue(myTurn);

        int activePlayer = state.activePlayerUid.equals(GameSessionManager.UID_PLAYER1) ? 1 : 2;
        roundLabel.postValue("Runda " + state.round + "/2  —  Igrač " + activePlayer);

        // Parsiraj ponuđene brojeve iz stringa
        if (state.offeredNumbers != null && !state.offeredNumbers.isEmpty()) {
            offeredNumbers.postValue(parseNumbers(state.offeredNumbers));
        }

        if (state.targetNumber > 0) {
            targetNumber.postValue(state.targetNumber);
        }

        switch (state.phase) {
            case "IDLE":
                phase.postValue(Phase.IDLE);
                if (myTurn) {
                    infoText.postValue("Tvoj red! Pritisni STOP da vidiš traženi broj.");
                } else {
                    infoText.postValue("Igrač " + activePlayer + " pritiska STOP...");
                }
                break;

            case "REVEAL_TARGET":
                phase.postValue(Phase.REVEAL_TARGET);
                if (myTurn) {
                    infoText.postValue("Traženi: " + state.targetNumber
                            + " — pritisni STOP za ponuđene brojeve.");
                } else {
                    infoText.postValue("Traženi: " + state.targetNumber
                            + " — čekamo igrača " + activePlayer + "...");
                }
                break;

            case "PLAYING":
                phase.postValue(Phase.PLAYING);
                if (myTurn) {
                    infoText.postValue("Napravi " + state.targetNumber
                            + " od ponuđenih brojeva!");
                } else {
                    infoText.postValue("Igrač " + activePlayer
                            + " rešava... Čekaj.");
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
                break;
        }
    }

    private String buildRoundSummary(MojBrojGameState state) {
        String p1res = state.player1RoundResult == -1
                ? "nije uneo" : String.valueOf(state.player1RoundResult);
        String p2res = state.player2RoundResult == -1
                ? "nije uneo" : String.valueOf(state.player2RoundResult);
        return String.format(
                "Runda gotova! Traženi: %d | P1: %s | P2: %s | Ukupno: %d – %d",
                state.targetNumber, p1res, p2res,
                state.player1Score, state.player2Score);
    }

    // ── Akcije igrača ─────────────────────────────────────────────────

    /**
     * Aktivni igrač pritisne STOP.
     * IDLE → generiše traženi broj i prelazi u REVEAL_TARGET
     * REVEAL_TARGET → generiše ponuđene brojeve i prelazi u PLAYING
     */
    public void onStop() {
        if (!remoteState.activePlayerUid.equals(sessionMgr.getMyUid())) return;

        MojBrojGameState newState = copyState(remoteState);

        if ("IDLE".equals(remoteState.phase)) {
            newState.targetNumber = 100 + rng.nextInt(900);
            newState.phase = "REVEAL_TARGET";
            sessionRepo.updateMojBrojState(newState);

        } else if ("REVEAL_TARGET".equals(remoteState.phase)) {
            newState.offeredNumbers = generateOfferedNumbers();
            newState.phase = "PLAYING";
            sessionRepo.updateMojBrojState(newState);
        }
    }

    /** Auto-reveal kad 5s istekne — poziva Fragment */
    public void onTargetAutoReveal() {
        if ("IDLE".equals(remoteState.phase)) onStop();
    }

    public void onNumbersAutoReveal() {
        if ("REVEAL_TARGET".equals(remoteState.phase)) onStop();
    }

    /** Live evaluacija dok igrač kuca */
    public void onExpressionChanged(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            liveExprResult.setValue(null);
            return;
        }
        try {
            ExpressionEvaluator ev = new ExpressionEvaluator(expr);
            double val = ev.evaluate();
            if (val == Math.floor(val) && val > 0) {
                liveExprResult.setValue((int) val);
            } else {
                liveExprResult.setValue(null);
            }
        } catch (ExpressionEvaluator.EvalException e) {
            liveExprResult.setValue(null);
        }
    }

    /**
     * Igrač potvrđuje izraz.
     * Vraća evaluiran rezultat, ili -1 ako je neispravan (faza se ne menja).
     */
    public int submitExpression(String expr) {
        if (!remoteState.activePlayerUid.equals(sessionMgr.getMyUid())) return -1;

        int result = -1;
        if (expr != null && !expr.trim().isEmpty()) {
            try {
                ExpressionEvaluator ev = new ExpressionEvaluator(expr);
                double val = ev.evaluate();
                if (val == Math.floor(val) && val > 0) {
                    result = (int) val;
                } else {
                    return -1; // decimalan ili negativan — ne menjaj fazu
                }
            } catch (ExpressionEvaluator.EvalException e) {
                return -1; // neispravan izraz — ne menjaj fazu
            }
        }

        finalizeRound(result);
        return result;
    }

    /** Timer istekao — prihvati šta god je uneseno */
    public void onRoundTimerFinished(String currentExpr) {
        if (!remoteState.activePlayerUid.equals(sessionMgr.getMyUid())) return;

        int result = -1;
        if (currentExpr != null && !currentExpr.trim().isEmpty()) {
            try {
                ExpressionEvaluator ev = new ExpressionEvaluator(currentExpr);
                double val = ev.evaluate();
                if (val == Math.floor(val) && val > 0) result = (int) val;
            } catch (ExpressionEvaluator.EvalException ignored) {}
        }
        finalizeRound(result);
    }

    private void finalizeRound(int myResult) {
        MojBrojGameState newState = copyState(remoteState);

        if (iAmPlayer1) {
            newState.player1RoundResult = myResult;
        } else {
            newState.player2RoundResult = myResult;
        }

        // Izračunaj bodove i upiši
        int pts1 = scoreFor(myResult,
                iAmPlayer1 ? -999 : newState.player2RoundResult, // opponent još nije igrao u r1
                newState.targetNumber, true);
        int pts2 = scoreFor(iAmPlayer1 ? -999 : myResult,
                iAmPlayer1 ? myResult : -999,
                newState.targetNumber, false);

        // Jednostavnije: skor za aktivnog igrača
        int myPts = calcScore(myResult, newState.targetNumber);
        if (iAmPlayer1) {
            newState.player1Score += myPts;
        } else {
            newState.player2Score += myPts;
        }

        newState.phase = "ROUND_END";
        sessionRepo.updateMojBrojState(newState);
    }

    /**
     * Poziva Fragment posle 3s pauze — samo player1 pokreće sledeću rundu.
     */
    public void onRoundEndCountdownFinished() {
        if (!"ROUND_END".equals(remoteState.phase)) return;
        if (!iAmPlayer1) return;

        if (remoteState.round == 1) {
            MojBrojGameState newState = copyState(remoteState);
            newState.round = 2;
            newState.activePlayerUid = GameSessionManager.UID_PLAYER2;
            newState.phase = "IDLE";
            newState.targetNumber = 0;
            newState.offeredNumbers = "";
            newState.player1RoundResult = -1;
            newState.player2RoundResult = -1;
            sessionRepo.updateMojBrojState(newState);
        } else {
            MojBrojGameState newState = copyState(remoteState);
            newState.phase = "GAME_OVER";
            newState.status = "finished";
            sessionRepo.updateMojBrojState(newState);
        }
    }

    // ── Bodovanje ─────────────────────────────────────────────────────

    /**
     * Bodovi za aktivnog igrača u rundi.
     * Pošto igrači igraju jedan po jedan (ne simultano),
     * svaki dobija bodove samo za svoju rundu.
     */
    private int calcScore(int myResult, int target) {
        if (myResult == target) return 10;
        if (myResult == -1) return 0;
        return 5; // bliži od protivnika — u ovoj implementaciji svako igra sam pa dobija 5
    }

    // Ostavljamo za kompatibilnost
    private int scoreFor(int my, int their, int target, boolean iAmActive) { return 0; }

    // ── Helpers ───────────────────────────────────────────────────────

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

        // Shuffle
        for (int i = 5; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = nums[i]; nums[i] = nums[j]; nums[j] = tmp;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(nums[i]);
            if (i < 5) sb.append(",");
        }
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
        copy.activePlayerUid     = src.activePlayerUid;
        copy.phase               = src.phase;
        copy.targetNumber        = src.targetNumber;
        copy.offeredNumbers      = src.offeredNumbers;
        copy.player1Score        = src.player1Score;
        copy.player2Score        = src.player2Score;
        copy.player1RoundResult  = src.player1RoundResult;
        copy.player2RoundResult  = src.player2RoundResult;
        return copy;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (firestoreListener != null) firestoreListener.remove();
    }
}