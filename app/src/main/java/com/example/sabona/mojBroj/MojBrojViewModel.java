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
        IDLE,            // čekamo STOP za traženi broj (samo activePlayer)
        REVEAL_TARGET,   // traženi broj otkriven, čekamo STOP za ponuđene (samo activePlayer)
        PLAYING,         // OBA igrača unose izraz istovremeno (60s)
        ROUND_END,       // runda gotova — prikazujemo rezultate
        GAME_OVER        // obe runde gotove
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
    // true kad sam ja lično završio unos (submit ili timer) — da Fragment zna da zaključa UI
    private final MutableLiveData<Boolean> iDoneLocal     = new MutableLiveData<>(false);

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
    public LiveData<Boolean> getIDoneLocal()      { return iDoneLocal; }

    public int getActivePlayerNumber() {
        return remoteState.activePlayerUid.equals(GameSessionManager.UID_PLAYER1) ? 1 : 2;
    }

    // ── Init ──────────────────────────────────────────────────────────

    public void init() {
        iAmPlayer1 = sessionMgr.isPlayer1();

        // Player1 kreira početno stanje u Firestoru
        if (iAmPlayer1 && !initialized) {
            initialized = true;
            MojBrojGameState initState = new MojBrojGameState();
            initState.status             = "playing";
            initState.round              = 1;
            initState.activePlayerUid    = GameSessionManager.UID_PLAYER1;
            initState.phase              = "IDLE";
            initState.targetNumber       = 0;
            initState.offeredNumbers     = "";
            initState.player1Score       = 0;
            initState.player2Score       = 0;
            initState.player1RoundResult = -1;
            initState.player2RoundResult = -1;
            initState.player1Done        = false;
            initState.player2Done        = false;
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
        // isMyTurn = jesam li ja activePlayer (onaj ko stopira)
        boolean myTurn = state.activePlayerUid.equals(sessionMgr.getMyUid());
        isMyTurn.postValue(myTurn);

        int activePlayer = state.activePlayerUid.equals(GameSessionManager.UID_PLAYER1) ? 1 : 2;
        roundLabel.postValue("Runda " + state.round + "/2  —  Igrač " + activePlayer + " stopira");

        // Parsiraj ponuđene brojeve
        if (state.offeredNumbers != null && !state.offeredNumbers.isEmpty()) {
            offeredNumbers.postValue(parseNumbers(state.offeredNumbers));
        }

        if (state.targetNumber > 0) {
            targetNumber.postValue(state.targetNumber);
        }

        switch (state.phase) {
            case "IDLE":
                iDoneLocal.postValue(false);
                phase.postValue(Phase.IDLE);
                if (myTurn) {
                    infoText.postValue("Tvoj red! Pritisni STOP da vidiš traženi broj.");
                } else {
                    infoText.postValue("Igrač " + activePlayer + " pritiska STOP za traženi broj...");
                }
                break;

            case "REVEAL_TARGET":
                phase.postValue(Phase.REVEAL_TARGET);
                if (myTurn) {
                    infoText.postValue("Traženi: " + state.targetNumber
                            + " — pritisni STOP za ponuđene brojeve.");
                } else {
                    infoText.postValue("Traženi: " + state.targetNumber
                            + " — čekamo igrača " + activePlayer + " da otkrije brojeve...");
                }
                break;

            case "PLAYING":
                // OBA igrača su u PLAYING — svako ima 60s tajmer lokalno
                phase.postValue(Phase.PLAYING);
                boolean myDone = iAmPlayer1 ? state.player1Done : state.player2Done;
                boolean opDone = iAmPlayer1 ? state.player2Done : state.player1Done;

                if (myDone) {
                    infoText.postValue(opDone
                            ? "Oba igrača su završila — čekamo rezultate..."
                            : "Predao/la si. Čekamo protivnika...");
                } else {
                    infoText.postValue("Napravi " + state.targetNumber
                            + " od ponuđenih brojeva! (" + (opDone ? "Protivnik je završio" : "Oba igraju") + ")");
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
     * Aktivni igrač (activePlayer) pritisne STOP ili shake.
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
            newState.offeredNumbers  = generateOfferedNumbers();
            newState.phase           = "PLAYING";
            newState.player1Done     = false;
            newState.player2Done     = false;
            newState.player1RoundResult = -1;
            newState.player2RoundResult = -1;
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
     * Igrač potvrđuje izraz (submit dugme).
     * Vraća evaluiran rezultat, ili -1 ako je neispravan (faza se ne menja).
     * Oba igrača mogu ovo da urade nezavisno tokom PLAYING faze.
     */
    public int submitExpression(String expr) {
        if (!"PLAYING".equals(remoteState.phase)) return -1;
        // Proveri da li sam već predao
        boolean alreadyDone = iAmPlayer1 ? remoteState.player1Done : remoteState.player2Done;
        if (alreadyDone) return -1;

        int result = evaluateExpr(expr);
        if (result == -1) return -1; // neispravan izraz — ne menjaj ništa

        recordMyResult(result);
        return result;
    }

    /**
     * Tajmer za 60s istekao za ovog igrača — prihvati šta god je uneseno.
     * Svaki igrač poziva ovo lokalno kada mu istekne timer.
     */
    public void onRoundTimerFinished(String currentExpr) {
        if (!"PLAYING".equals(remoteState.phase)) return;
        boolean alreadyDone = iAmPlayer1 ? remoteState.player1Done : remoteState.player2Done;
        if (alreadyDone) return;

        int result = evaluateExpr(currentExpr); // može biti -1 ako je prazan/neispravan
        recordMyResult(result);
    }

    /**
     * Upis svog rezultata u Firestore.
     * Ako su oba igrača done → izračunaj bodove i prebaci u ROUND_END.
     */
    private void recordMyResult(int myResult) {
        MojBrojGameState newState = copyState(remoteState);

        if (iAmPlayer1) {
            newState.player1RoundResult = myResult;
            newState.player1Done = true;
        } else {
            newState.player2RoundResult = myResult;
            newState.player2Done = true;
        }

        // Javi lokalno da sam završio (da Fragment zaključa UI odmah)
        iDoneLocal.postValue(true);

        // Ako su oba završila → izračunaj bodove i idi u ROUND_END
        boolean bothDone = (iAmPlayer1 ? newState.player1Done : newState.player2Done)
                && (iAmPlayer1 ? remoteState.player2Done : remoteState.player1Done)
                // Proverimo i current newState (da ne preskočimo race condition)
                || (newState.player1Done && newState.player2Done);

        if (bothDone) {
            applyScoring(newState);
            newState.phase = "ROUND_END";
        }

        sessionRepo.updateMojBrojState(newState);
    }

    /**
     * Bodovanje po specifikaciji:
     *
     * - Ako igrač pogodi traženi broj → 10 bodova.
     * - Ako prvi igrač ne pogodi, a drugi pogodi → drugi 10 bodova.
     * - Ako oba ne pogode:
     *     - Onaj čiji je rezultat bliži dobija 5 bodova.
     *     - Ako su isti rezultat (≠ 0) → 5 bodova dobija igrač ČIJA je runda (activePlayer).
     *     - Ako igrač ništa nije uneo (-1) → 0 bodova.
     * - Ako oba ništa nisu unela → 0-0.
     */
    private void applyScoring(MojBrojGameState s) {
        int target = s.targetNumber;
        int r1 = s.player1RoundResult; // -1 = nije uneo
        int r2 = s.player2RoundResult;

        boolean p1Hit = (r1 == target);
        boolean p2Hit = (r2 == target);

        if (p1Hit && p2Hit) {
            // Oba pogodila — po spec nije eksplicitno, ali svako dobija 10
            s.player1Score += 10;
            s.player2Score += 10;
        } else if (p1Hit) {
            s.player1Score += 10;
        } else if (p2Hit) {
            s.player2Score += 10;
        } else {
            // Niko nije pogodio
            if (r1 == -1 && r2 == -1) {
                // Oba bez unosa — 0-0
                return;
            }
            if (r1 == -1) {
                // Samo P2 nešto uneo → P2 dobija 5 (bliži od 0 bodova P1)
                s.player2Score += 5;
                return;
            }
            if (r2 == -1) {
                // Samo P1 nešto uneo → P1 dobija 5
                s.player1Score += 5;
                return;
            }

            int diff1 = Math.abs(r1 - target);
            int diff2 = Math.abs(r2 - target);

            if (diff1 < diff2) {
                s.player1Score += 5;
            } else if (diff2 < diff1) {
                s.player2Score += 5;
            } else {
                // Isti rezultat (i različit od targeta) → bodove dobija čija je runda
                boolean activeIsP1 = s.activePlayerUid.equals(GameSessionManager.UID_PLAYER1);
                if (activeIsP1) {
                    s.player1Score += 5;
                } else {
                    s.player2Score += 5;
                }
            }
        }
    }

    /**
     * Poziva Fragment posle 3s pauze na kraju runde.
     * Samo player1 pokreće sledeću rundu (da ne bi oba pisala).
     * Ali: ako su oba done a ROUND_END još nije upisan, player1 to radi.
     */
    public void onRoundEndCountdownFinished() {
        if (!"ROUND_END".equals(remoteState.phase)) return;
        if (!iAmPlayer1) return;

        if (remoteState.round == 1) {
            MojBrojGameState newState = copyState(remoteState);
            newState.round               = 2;
            newState.activePlayerUid     = GameSessionManager.UID_PLAYER2;
            newState.phase               = "IDLE";
            newState.targetNumber        = 0;
            newState.offeredNumbers      = "";
            newState.player1RoundResult  = -1;
            newState.player2RoundResult  = -1;
            newState.player1Done         = false;
            newState.player2Done         = false;
            sessionRepo.updateMojBrojState(newState);
        } else {
            MojBrojGameState newState = copyState(remoteState);
            newState.phase  = "GAME_OVER";
            newState.status = "finished";
            sessionRepo.updateMojBrojState(newState);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** Evaluira izraz, vraća ceo pozitivan rezultat ili -1 ako nije validan */
    private int evaluateExpr(String expr) {
        if (expr == null || expr.trim().isEmpty()) return -1;
        try {
            ExpressionEvaluator ev = new ExpressionEvaluator(expr);
            double val = ev.evaluate();
            if (val == Math.floor(val) && val > 0) {
                return (int) val;
            }
        } catch (ExpressionEvaluator.EvalException ignored) {}
        return -1;
    }

    private String generateOfferedNumbers() {
        int[] nums = new int[6];
        // 4 jednocifrena
        nums[0] = 1 + rng.nextInt(9);
        nums[1] = 1 + rng.nextInt(9);
        nums[2] = 1 + rng.nextInt(9);
        nums[3] = 1 + rng.nextInt(9);
        // jedan srednji: 10, 15 ili 20
        int[] medium = {10, 15, 20};
        nums[4] = medium[rng.nextInt(3)];
        // jedan veliki: 25, 50, 75 ili 100
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
        copy.player1Done         = src.player1Done;
        copy.player2Done         = src.player2Done;
        return copy;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (firestoreListener != null) firestoreListener.remove();
    }
}