package com.example.sabona.spojnice;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.spojnice.SpojniceRepository.SpojniceQuestion;
import com.example.sabona.repository.StatsRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel za Spojnice.
 *
 * Fragment je samo prezentacijski sloj — sve što je ranije bilo u SpojniceFragment
 * (Firestore, timer, faze, bodovanje, solo logika) živi ovdje.
 *
 * LiveData koje Fragment observuje:
 *   uiPhase         → LOADING | WAITING | PLAYING | GAME_OVER
 *   waitingMsg      → tekst na čekaonici
 *   infoText        → tekst u donjem info TextViewu
 *   timerText       → "00:30", "00:29"...
 *   headerState     → podaci za tvRound, tvPlayer, tvScore
 *   boardState      → kompletno stanje ploče (lijevo/desno dugmad, strelice)
 *   criteriaText    → kriterijum pitanja
 *   connectionEvent → one-shot: koji par je upravo spojen (za vizualni update)
 *   gameOverEvent   → one-shot: summary string za Toast + navigaciju
 */
public class SpojniceViewModel extends ViewModel {

    // ── Faze UI ─────────────────────────────────────────────────────────────
    public enum UiPhase { LOADING, WAITING, PLAYING, GAME_OVER }

    // ── One-shot event ───────────────────────────────────────────────────────
    public static class Event<T> {
        private final T content;
        private boolean handled = false;
        public Event(T content) { this.content = content; }
        public T getIfNotHandled() {
            if (handled) return null;
            handled = true;
            return content;
        }
        public T peek() { return content; }
    }

    // ── Board state (šalje se Fragmentu odjednom) ────────────────────────────
    public static class BoardState {
        public final boolean[] connected;
        public final int[]     connectedBy;
        public final boolean[] attempted;
        public final int[]     rightDisplayOrder;
        public final boolean   myTurn;
        public final int       round;
        public final String    phaseStr;

        public BoardState(boolean[] connected, int[] connectedBy, boolean[] attempted,
                          int[] rightDisplayOrder, boolean myTurn, int round, String phaseStr) {
            this.connected        = connected.clone();
            this.connectedBy      = connectedBy.clone();
            this.attempted        = attempted.clone();
            this.rightDisplayOrder = rightDisplayOrder.clone();
            this.myTurn           = myTurn;
            this.round            = round;
            this.phaseStr         = phaseStr;
        }
    }

    public static class HeaderState {
        public final int    round;
        public final String playerLabel;
        public final int    p1Score;
        public final int    p2Score;
        public HeaderState(int round, String playerLabel, int p1Score, int p2Score) {
            this.round       = round;
            this.playerLabel = playerLabel;
            this.p1Score     = p1Score;
            this.p2Score     = p2Score;
        }
    }

    // ── LiveData ─────────────────────────────────────────────────────────────
    private final MutableLiveData<UiPhase>         uiPhase         = new MutableLiveData<>(UiPhase.LOADING);
    private final MutableLiveData<String>          waitingMsg      = new MutableLiveData<>("");
    private final MutableLiveData<String>          infoText        = new MutableLiveData<>("Učitavanje...");
    private final MutableLiveData<String>          timerText       = new MutableLiveData<>("00:30");
    private final MutableLiveData<String>          criteriaText    = new MutableLiveData<>("");
    private final MutableLiveData<HeaderState>     headerState     = new MutableLiveData<>();
    private final MutableLiveData<BoardState>      boardState      = new MutableLiveData<>();
    private final MutableLiveData<Event<Integer>>  connectionEvent = new MutableLiveData<>(); // leftIndex koji je spojen
    private final MutableLiveData<Event<String>>   gameOverEvent   = new MutableLiveData<>();

    public LiveData<UiPhase>        getUiPhase()        { return uiPhase; }
    public LiveData<String>         getWaitingMsg()     { return waitingMsg; }
    public LiveData<String>         getInfoText()       { return infoText; }
    public LiveData<String>         getTimerText()      { return timerText; }
    public LiveData<String>         getCriteriaText()   { return criteriaText; }
    public LiveData<HeaderState>    getHeaderState()    { return headerState; }
    public LiveData<BoardState>     getBoardState()     { return boardState; }
    public LiveData<Event<Integer>> getConnectionEvent(){ return connectionEvent; }
    public LiveData<Event<String>>  getGameOverEvent()  { return gameOverEvent; }

    private boolean opponentHasLeft = false;
    // ── Firestore ─────────────────────────────────────────────────────────────
    private final FirebaseFirestore db   = FirebaseFirestore.getInstance();
    private final FirebaseAuth      auth = FirebaseAuth.getInstance();
    private DocumentReference    sessionRef;
    private ListenerRegistration sessionListener;
    private ListenerRegistration rootListener;

    // ── Repository ────────────────────────────────────────────────────────────
    private final SpojniceRepository repository = new SpojniceRepository();

    // ── Faze (string konstante) ───────────────────────────────────────────────
    private static final String PHASE_WAIT = "waiting_p2";
    private static final String PHASE_R1_A = "r1_phA";
    private static final String PHASE_R1_B = "r1_phB";
    private static final String PHASE_R2_A = "r2_phA";
    private static final String PHASE_R2_B = "r2_phB";
    private static final String PHASE_DONE = "finished";

    // ── Session state ─────────────────────────────────────────────────────────
    private String  myUid;
    private String  sessionId;
    private boolean isHost;
    //private boolean soloMode  = false;
    private boolean gameEnded = false;

    // ── Pitanja ───────────────────────────────────────────────────────────────
    private List<SpojniceQuestion> allQuestions = new ArrayList<>();
    private List<SpojniceQuestion> questions    = new ArrayList<>();

    // ── Game state ────────────────────────────────────────────────────────────
    private int     round          = 1;
    private int     activePlayer   = 1;
    private boolean myTurn         = false;
    private int     player1Score   = 0;
    private int     player2Score   = 0;
    private int     roundScore1    = 0;
    private int     roundScore2    = 0;
    private boolean[] connected    = new boolean[5];
    private int[]     connectedBy  = new int[5];
    private int       connectedCount = 0;
    private int       selectedLeft = -1;
    private boolean   roundFinished = false;
    private String    currentPhaseStr = "";
    private boolean[] attempted    = new boolean[5];
    private int[]     rightDisplayOrder = new int[5];
    private int[]     savedRound2Order  = new int[5];

    // ── Timer ─────────────────────────────────────────────────────────────────
    private CountDownTimer timer;

    // ═════════════════════════════════════════════════════════════════════════
    // Init
    // ═════════════════════════════════════════════════════════════════════════

    public void init(String uid) {
        if (myUid != null) return; // rotation guard
        myUid = uid != null ? uid : "unknown";
    }

    public String getMyUid() { return myUid != null ? myUid : "unknown"; }

    public void loadQuestions(LoadCallback callback) {
        repository.fetchQuestionsWithIds(new SpojniceRepository.SpojniceWithIdsCallback() {
            @Override
            public void onSuccess(List<SpojniceQuestion> result) {
                if (result.size() < 2) {
                    infoText.setValue("Nema dovoljno pitanja (min. 2).");
                    return;
                }
                allQuestions = result;
                callback.onReady();
            }
            @Override
            public void onError(Exception e) {
                infoText.setValue("Greška: " + e.getMessage());
            }
        });
    }

    public interface LoadCallback { void onReady(); }

    // ═════════════════════════════════════════════════════════════════════════
    // Akcije koje Fragment poziva
    // ═════════════════════════════════════════════════════════════════════════

    public void createSession(String sid) {
        sessionId = sid;
        isHost    = true;
        sessionRef = sessionRef(sid);

        List<SpojniceQuestion> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        SpojniceQuestion q0 = shuffled.get(0);
        SpojniceQuestion q1 = shuffled.get(1);
        questions.clear();
        questions.add(q0);
        questions.add(q1);

        List<Integer> order1 = shuffledOrder();
        List<Integer> order2 = shuffledOrder();

        Map<String, Object> data = new HashMap<>();
        data.put("phase",          PHASE_WAIT);
        data.put("p1Score",        0);
        data.put("p2Score",        0);
        data.put("connected",      buildFalseBoolList());
        data.put("connectedBy",    buildZeroIntList());
        data.put("connectedCount", 0);
        data.put("round1Order",    order1);
        data.put("round2Order",    order2);
        data.put("question0Id",    q0.docId != null ? q0.docId : "");
        data.put("question1Id",    q1.docId != null ? q1.docId : "");
        data.put("hostUid",        myUid);

        sessionRef.set(data)
                .addOnSuccessListener(v -> {
                    waitingMsg.setValue("Čekam Igrača 2...\nKod: " + sessionId);
                    uiPhase.setValue(UiPhase.WAITING);
                    startListening();
                    listenRootForAbandon();
                })
                .addOnFailureListener(e -> infoText.setValue("Greška: " + e.getMessage()));

    }

    public void joinSession(String sid) {
        sessionId = sid;
        isHost    = false;
        sessionRef = sessionRef(sid);

        sessionRef.get().addOnSuccessListener(snap -> {
            if (!snap.exists()) { infoText.setValue("Sesija nije nađena. Provjeri kod!"); return; }
            doJoinSession(snap);
        }).addOnFailureListener(e -> infoText.setValue("Sesija nije nađena. Provjeri kod!"));
    }

    /** Koristi se kad je sesija proslijeđena od KoZnaZna (host side) */
    public void createSessionSilent(String sid) {
        sessionId = sid;
        isHost    = true;
        sessionRef = sessionRef(sid);

        List<SpojniceQuestion> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        SpojniceQuestion q0 = shuffled.get(0);
        SpojniceQuestion q1 = shuffled.get(1);
        questions.clear();
        questions.add(q0);
        questions.add(q1);

        List<Integer> order1 = shuffledOrder();
        List<Integer> order2 = shuffledOrder();

        boolean isSolo = com.example.sabona.game.GameSessionManager.get().isSoloSession();

        Map<String, Object> data = new HashMap<>();
        data.put("phase",          isSolo ? PHASE_R1_A : PHASE_WAIT);
        data.put("p1Score",        0);
        data.put("p2Score",        0);
        data.put("connected",      buildFalseBoolList());
        data.put("connectedBy",    buildZeroIntList());
        data.put("connectedCount", 0);
        data.put("round1Order",    order1);
        data.put("round2Order",    order2);
        data.put("question0Id",    q0.docId != null ? q0.docId : "");
        data.put("question1Id",    q1.docId != null ? q1.docId : "");
        data.put("hostUid",        myUid);

        sessionRef.set(data)
                .addOnSuccessListener(v -> {
                    if (isSolo) {
                        startListening();
                        listenRootForAbandon();
                    } else {
                        waitingMsg.setValue("Čekam Igrača 2... (Spojnice)");
                        uiPhase.setValue(UiPhase.WAITING);
                        startListening();
                        listenRootForAbandon();
                    }
                })
                .addOnFailureListener(e -> infoText.setValue("Greška: " + e.getMessage()));
    }

    /** Koristi se kad je sesija proslijeđena od KoZnaZna (guest side) */
    public void joinExistingSession(String sid, boolean hostSide) {
        sessionId = sid;
        isHost    = hostSide;
        sessionRef = sessionRef(sid);

        infoText.setValue("Čekam host da kreira Spojnice...");
        listenRootForAbandon();
        waitForSessionThenJoin();
    }

    private void createSessionForRemainingGuest() {
        List<SpojniceQuestion> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        SpojniceQuestion q0 = shuffled.get(0);
        SpojniceQuestion q1 = shuffled.get(1);
        questions.clear();
        questions.add(q0);
        questions.add(q1);

        Map<String, Object> data = new HashMap<>();
        data.put("phase",          PHASE_R2_A);
        data.put("p1Score",        0);
        data.put("p2Score",        0);
        data.put("connected",      buildFalseBoolList());
        data.put("connectedBy",    buildZeroIntList());
        data.put("connectedCount", 0);
        data.put("round1Order",    shuffledOrder());
        data.put("round2Order",    shuffledOrder());
        data.put("question0Id",    q0.docId != null ? q0.docId : "");
        data.put("question1Id",    q1.docId != null ? q1.docId : "");
        data.put("hostUid",        myUid);

        sessionRef.set(data)
                .addOnSuccessListener(v -> {
                    startListening();
                    listenRootForAbandon();
                })
                .addOnFailureListener(e -> infoText.setValue("Greska: " + e.getMessage()));
    }

    /*public void startSoloGame() {
        soloMode = true;
        isHost   = true;
        player1Score = 0;
        player2Score = 0;

        List<SpojniceQuestion> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        questions.clear();
        questions.add(shuffled.get(0));
        questions.add(shuffled.get(1));

        List<Integer> order1 = shuffledOrder();
        List<Integer> order2 = shuffledOrder();
        for (int i = 0; i < 5; i++) {
            rightDisplayOrder[i] = order1.get(i);
            savedRound2Order[i]  = order2.get(i);
        }
        currentPhaseStr = PHASE_R1_A;
        applyPhase(PHASE_R1_A);
    }*/

    /** Klik na lijevo dugme */
    public void onLeftClick(int index) {
        if (!myTurn || roundFinished) return;
        if (connected[index] || attempted[index]) return;

        if (selectedLeft == index) {
            selectedLeft = -1;
            infoText.setValue("🎯 Tvoj red! Klikni lijevo → desno.");
            emitBoard();
            return;
        }

        selectedLeft = index;
        SpojniceQuestion q = questions.get(round - 1);
        infoText.setValue("Odabrano: \"" + q.leftItems.get(index) + "\". Klikni par desno.");
        emitBoard();
    }

    /** Klik na desno dugme (displayPos = pozicija u UI, ne originalRightIndex) */
    public void onRightClick(int displayPos) {
        if (!myTurn || roundFinished) return;
        if (selectedLeft == -1) {
            infoText.setValue("Prvo klikni pojam lijevo!");
            return;
        }

        int originalRightIndex = rightDisplayOrder[displayPos];
        SpojniceQuestion q = questions.get(round - 1);

        // Provjeri da desni pojam nije već spojen
        for (int i = 0; i < 5; i++) {
            if (connected[i] && q.correctPairs[i] == originalRightIndex) {
                infoText.setValue("Ovaj pojam je već spojen!");
                return;
            }
        }

        int pairIndex = selectedLeft;
        selectedLeft = -1;

        if (q.correctPairs[pairIndex] == originalRightIndex) {
            // ✓ Tačno
            connected[pairIndex]   = true;
            connectedBy[pairIndex] = activePlayer;
            connectedCount++;

            if (activePlayer == 1) roundScore1 += 2;
            else                   roundScore2 += 2;

            connectionEvent.setValue(new Event<>(pairIndex));
            emitBoard();
            emitHeader();
            saveConnectionToFirestore(pairIndex);
        } else {
            // ✗ Netačno
            attempted[pairIndex] = true;
            infoText.setValue("❌ Netačno! Taj pojam više nije dostupan.");
            emitBoard();

            // Ako je ovim pokušajem igrač "obradio" sve parove (svaki je
            // ili spojen, ili već netačno pokušan), nema više validnih
            // poteza za njega — nema smisla čekati da istekne ceo tajmer.
            // Pređi na sledeću fazu nakon kratke pauze (2s), da igrač
            // stigne da vidi crveni "✗" pre prelaska.
            if (allPairsResolved() && !roundFinished) {
                infoText.setValue("❌ Netačno! Nema više pokušaja — prelazimo dalje...");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!roundFinished) advancePhase(false, false, true);
                }, 2000);
            }
        }
    }

    /** Da li je svaki od 5 parova ili spojen, ili već netačno pokušan (nema više validnih poteza). */
    private boolean allPairsResolved() {
        for (int i = 0; i < 5; i++) {
            if (!connected[i] && !attempted[i]) return false;
        }
        return true;
    }

    public int getSelectedLeft() { return selectedLeft; }

    public SpojniceQuestion getCurrentQuestion() {
        if (questions.isEmpty() || questions.size() < round) return null;
        return questions.get(round - 1);
    }

    public String getSessionId() { return sessionId; }
    public boolean isHost()      { return isHost; }
    //public boolean isSoloMode()  { return soloMode; }

    // ═════════════════════════════════════════════════════════════════════════
    // Firestore helpers
    // ═════════════════════════════════════════════════════════════════════════

    private DocumentReference sessionRef(String sid) {
        return db.collection("gameSessions")
                .document(sid)
                .collection("games")
                .document("spojnice");
    }

    private void waitForSessionThenJoin() {
        sessionRef.get().addOnSuccessListener(snap -> {
            if (snap.exists()) {
                doJoinSession(snap);
            } else {
                if (opponentHasLeft) {
                    createSessionForRemainingGuest();
                    return;
                }
                infoText.setValue("Čekam host... (pokušaj ponovo)");
                new Handler(Looper.getMainLooper())
                        .postDelayed(this::waitForSessionThenJoin, 1000);
            }
        }).addOnFailureListener(e -> infoText.setValue("Greška spajanja: " + e.getMessage()));
    }

    private void doJoinSession(DocumentSnapshot snap) {
        String q0Id = snap.getString("question0Id");
        String q1Id = snap.getString("question1Id");
        SpojniceQuestion q0 = findById(q0Id);
        SpojniceQuestion q1 = findById(q1Id);
        if (q0 == null || q1 == null) {
            infoText.setValue("Greška: pitanja nisu nađena lokalno.");
            return;
        }
        questions.clear();
        questions.add(q0);
        questions.add(q1);

        Map<String, Object> update = new HashMap<>();
        update.put("phase",    PHASE_R1_A);
        update.put("guestUid", myUid);

        sessionRef.update(update)
                .addOnSuccessListener(v -> {
                    infoText.setValue("Pridružen! Počinjemo...");
                    startListening();
                    listenRootForAbandon();
                })
                .addOnFailureListener(e -> infoText.setValue("Greška: " + e.getMessage()));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Realtime listener
    // ═════════════════════════════════════════════════════════════════════════

    private void startListening() {
        sessionListener = sessionRef.addSnapshotListener((snap, err) -> {
            if (snap == null || !snap.exists()) return;

            String newPhase = snap.getString("phase");
            if (newPhase == null) return;

            player1Score = toInt(snap.get("p1Score"));
            player2Score = toInt(snap.get("p2Score"));

            if (PHASE_WAIT.equals(newPhase)) {
                if (opponentHasLeft) {
                    if (isHost) {
                        sessionRef.update("phase", PHASE_R1_A);
                    }
                    return;
                }
                waitingMsg.setValue("Čekam Igrača 2...\nKod: " + sessionId);
                uiPhase.setValue(UiPhase.WAITING);
                return;
            }

            if (PHASE_DONE.equals(newPhase)) {
                triggerGameOver();
                return;
            }

            List<?> connList = (List<?>) snap.get("connected");
            List<?> byList   = (List<?>) snap.get("connectedBy");
            int     cCount   = toInt(snap.get("connectedCount"));

            boolean[] newConnected   = new boolean[5];
            int[]     newConnectedBy = new int[5];

            if (connList != null && connList.size() == 5 && byList != null && byList.size() == 5) {
                for (int i = 0; i < 5; i++) {
                    newConnected[i]   = Boolean.TRUE.equals(connList.get(i));
                    newConnectedBy[i] = toInt(byList.get(i));
                }
            }

            if (!newPhase.equals(currentPhaseStr)) {
                currentPhaseStr = newPhase;
                connectedCount  = cCount;
                connected       = newConnected;
                connectedBy     = newConnectedBy;

                String orderKey = newPhase.startsWith("r1") ? "round1Order" : "round2Order";
                List<?> orderList = (List<?>) snap.get(orderKey);
                if (orderList != null && orderList.size() == 5) {
                    for (int i = 0; i < 5; i++) rightDisplayOrder[i] = toInt(orderList.get(i));
                }

                applyPhase(newPhase);
            } else {
                boolean changed = false;
                for (int i = 0; i < 5; i++) {
                    if (!connected[i] && newConnected[i]) {
                        connected[i]   = true;
                        connectedBy[i] = newConnectedBy[i];
                        connectionEvent.setValue(new Event<>(i));
                        if (newConnectedBy[i] == 1) roundScore1 += 2;
                        else if (newConnectedBy[i] == 2) roundScore2 += 2;
                        changed = true;
                    }
                }
                if (changed) {
                    connectedCount = cCount;
                    emitHeader();
                    emitBoard();
                    if (connectedCount == 5 && !roundFinished) {
                        if (timer != null) timer.cancel();
                        timerText.setValue("00:00");
                        if (isHost) {
                            advancePhase(true);
                        } else {
                            roundFinished = true;
                            infoText.setValue("Runda završena...");
                        }
                    }
                }
                emitHeader();
            }
        });
    }

    private void listenRootForAbandon() {
        if (sessionId == null) return;
        rootListener = db.collection("gameSessions").document(sessionId)
                .addSnapshotListener((snap, e) -> {
                    if (snap == null || !snap.exists()) return;
                    String leftUid = snap.getString("leftByUid");
                    if (leftUid == null) return;
                    if (leftUid.equals(myUid)) return;

                    opponentHasLeft = true; // pamtimo trajno, ne samo za ovaj event

                    if (PHASE_WAIT.equals(currentPhaseStr) || currentPhaseStr == null || currentPhaseStr.isEmpty()) {
                        if (sessionRef != null) {
                            sessionRef.get().addOnSuccessListener(gameSnap -> {
                                if (gameSnap != null && gameSnap.exists()
                                        && PHASE_WAIT.equals(gameSnap.getString("phase"))) {
                                    sessionRef.update("phase", isHost ? PHASE_R1_A : PHASE_R2_A);
                                }
                            });
                        }
                        return;
                    }

                    if (!roundFinished) {
                        if (timer != null) timer.cancel();
                        infoText.postValue("Protivnik je napustio partiju. Nastavljaš ti.");
                        advancePhase(false, true);
                    }
                });
    }
    // ═════════════════════════════════════════════════════════════════════════
    // Faze
    // ═════════════════════════════════════════════════════════════════════════

    private void applyPhase(String ph) {
        if (timer != null) timer.cancel();
        roundFinished = false;
        selectedLeft  = -1;
        for (int i = 0; i < 5; i++) attempted[i] = false;

        round = ph.startsWith("r1") ? 1 : 2;

        if (PHASE_R1_A.equals(ph) || PHASE_R2_B.equals(ph)) activePlayer = 1;
        else                                                  activePlayer = 2;

        boolean isSoloNow = com.example.sabona.game.GameSessionManager.get().isSoloSession();
        myTurn = isSoloNow || (isHost && activePlayer == 1) || (!isHost && activePlayer == 2);

        if (PHASE_R1_A.equals(ph) || PHASE_R2_A.equals(ph)) {
            roundScore1    = 0;
            roundScore2    = 0;
            connectedCount = 0;
            for (int i = 0; i < 5; i++) { connected[i] = false; connectedBy[i] = 0; }
        } else {
            roundScore1 = 0;
            roundScore2 = 0;
            for (int i = 0; i < 5; i++) {
                if (connected[i]) {
                    if (connectedBy[i] == 1) roundScore1 += 2;
                    else if (connectedBy[i] == 2) roundScore2 += 2;
                }
            }
        }

        if (!questions.isEmpty() && questions.size() >= round) {
            criteriaText.setValue(questions.get(round - 1).criteria);
        }

        uiPhase.setValue(UiPhase.PLAYING);
        infoText.setValue(myTurn ? "🎯 Tvoj red! Klikni lijevo → desno." : "⏳ Čekam protivnika...");
        emitHeader();
        emitBoard();

        if (opponentHasLeft && !myTurn) {
            // Red je na igraču koji je napustio — ne čekaj, odmah dalje
            roundFinished = true;
            infoText.setValue("Protivnik je napustio partiju. Nastavljaš ti.");
            advancePhase(false, true);
            return;
        }

        startTimer(30);
    }

    private void advancePhase(boolean allConnected) {
        advancePhase(allConnected, false, false);
    }

    private void advancePhase(boolean allConnected, boolean opponentAbandoned) {
        advancePhase(allConnected, opponentAbandoned, false);
    }

    /**
     * @param triggeredByActivePlayer true kad ovaj poziv dolazi od strane
     *        igrača koji je TRENUTNO NA POTEZU, a iskoristio je sve svoje
     *        poteze (svi parovi spojeni ili pokušani) — bez obzira je li
     *        host ili ne. Ovo je bezbedno za upis (nema "trke" sa drugim
     *        klijentom, jer samo igrač na potezu generiše ovaj event), za
     *        razliku od isteka tajmera kod igrača koji NIJE na potezu (taj
     *        slučaj ostaje rezervisan za host, kao i ranije).
     */
    private void advancePhase(boolean allConnected, boolean opponentAbandoned,
                              boolean triggeredByActivePlayer) {
        if (timer != null) timer.cancel();
        roundFinished = true;

        int remaining = 0;
        for (boolean c : connected) if (!c) remaining++;

        boolean isSolo = com.example.sabona.game.GameSessionManager.get().isSoloSession();

        String nextPhase;
        if (isSolo) {
            // U solo modu: samo jedna runda, nepovezani parovi ostaju nepovezani — odmah završiti
            switch (currentPhaseStr) {
                case PHASE_R1_A: nextPhase = (allConnected || remaining == 0) ? PHASE_DONE : PHASE_DONE; break;
                case PHASE_R2_A: nextPhase = PHASE_DONE; break;
                default:         nextPhase = PHASE_DONE; break;
            }
        } else {
            switch (currentPhaseStr) {
                case PHASE_R1_A: nextPhase = (allConnected || remaining == 0) ? PHASE_R2_A : PHASE_R1_B; break;
                case PHASE_R1_B: nextPhase = PHASE_R2_A; break;
                case PHASE_R2_A: nextPhase = (allConnected || remaining == 0) ? PHASE_DONE : PHASE_R2_B; break;
                default:         nextPhase = PHASE_DONE; break;
            }
        }

        boolean shouldWrite = isHost || opponentAbandoned || opponentHasLeft
                || (triggeredByActivePlayer && myTurn);

        if (shouldWrite) {
            Map<String, Object> update = new HashMap<>();
            update.put("phase", nextPhase);
            if (!isSolo && PHASE_R2_A.equals(nextPhase)) {
                update.put("connected",      buildFalseBoolList());
                update.put("connectedBy",    buildZeroIntList());
                update.put("connectedCount", 0);
            }
            sessionRef.update(update);
        }
        infoText.setValue("Runda završena...");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Spasi konekciju u Firestore
    // ═════════════════════════════════════════════════════════════════════════

    private void saveConnectionToFirestore(int pairIndex) {
        /*if (soloMode) {
            if (connectedCount == 5) onAllConnected();
            return;
        }*/

        List<Boolean> newConnected   = new ArrayList<>();
        List<Integer> newConnectedBy = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            newConnected.add(connected[i]);
            newConnectedBy.add(connectedBy[i]);
        }

        String scoreField = (activePlayer == 1) ? "p1Score" : "p2Score";

        Map<String, Object> update = new HashMap<>();
        update.put("connected",      newConnected);
        update.put("connectedBy",    newConnectedBy);
        update.put("connectedCount", connectedCount);
        update.put(scoreField,       FieldValue.increment(2));

        sessionRef.update(update)
                .addOnSuccessListener(v -> {
                    if (connectedCount == 5) {
                        onAllConnected();
                    } else if (allPairsResolved() && !roundFinished) {
                        advancePhase(false, false, true);
                    }
                })
                .addOnFailureListener(e -> infoText.setValue("Greška spajanja: " + e.getMessage()));
    }

    private void onAllConnected() {
        if (!roundFinished) advancePhase(true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Timer
    // ═════════════════════════════════════════════════════════════════════════

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer((long) seconds * 1000, 1000) {
            @Override public void onTick(long ms) { timerText.setValue(String.format("00:%02d", ms / 1000 + 1)); }
            @Override public void onFinish() {
                timerText.setValue("00:00");
                onTimerExpired();
            }
        };
        timer.start();
    }

    private void onTimerExpired() {
        if (roundFinished) return;
        if (!myTurn && !isHost) { infoText.setValue("Čekam..."); return; }
        advancePhase(false);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Kraj igre
    // ═════════════════════════════════════════════════════════════════════════

    private void triggerGameOver() {
        if (gameEnded) return;
        gameEnded = true;
        if (timer != null) timer.cancel();

        int myScore     = isHost ? player1Score : player2Score;
        int myConnected = myScore / 2;

        final int myScoreFinal = myScore;
        final int myConnectedFinal = myConnected;
        db.collection("gameSessions").document(sessionId).get()
                .addOnSuccessListener(snap -> {
                    boolean friendly = snap.exists() && Boolean.TRUE.equals(snap.getBoolean("isFriendlyMatch"));
                    if (!friendly) {
                        try { new StatsRepository().saveSpojniceResult(myConnectedFinal, 10, myScoreFinal); } catch (Exception ignored) {}
                    }
                });

        if (isHost) {
            db.collection("gameSessions").document(sessionId)
                    .update("totalScoreP1", FieldValue.increment(player1Score),
                            "totalScoreP2", FieldValue.increment(player2Score));
        }

        String result;
        if (player1Score > player2Score)
            result = "Pobjednik spojnica: Igrač 1! (" + player1Score + " vs " + player2Score + ")";
        else if (player2Score > player1Score)
            result = "Pobjednik spojnica: Igrač 2! (" + player2Score + " vs " + player1Score + ")";
        else
            result = "Spojnice izjednačene! (" + player1Score + " : " + player2Score + ")";

        uiPhase.setValue(UiPhase.GAME_OVER);
        gameOverEvent.setValue(new Event<>(result));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Emit helpers — šalje ažuriran state Fragmentu
    // ═════════════════════════════════════════════════════════════════════════

    private void emitBoard() {
        boardState.setValue(new BoardState(
                connected, connectedBy, attempted,
                rightDisplayOrder, myTurn, round, currentPhaseStr));
    }

    private void emitHeader() {
        String label;
        switch (currentPhaseStr) {
            case PHASE_R1_A: label = "Na potezu: Igrač 1 (1. faza)"; break;
            case PHASE_R1_B: label = "Na potezu: Igrač 2 (preostali)"; break;
            case PHASE_R2_A: label = "Na potezu: Igrač 2 (1. faza)"; break;
            case PHASE_R2_B: label = "Na potezu: Igrač 1 (preostali)"; break;
            default:         label = "Runda završena"; break;
        }
        headerState.setValue(new HeaderState(round, label, player1Score, player2Score));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Cleanup
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCleared() {
        super.onCleared();
        if (timer != null) timer.cancel();
        if (sessionListener != null) sessionListener.remove();
        if (rootListener != null) rootListener.remove();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private SpojniceQuestion findById(String docId) {
        if (docId == null) return null;
        for (SpojniceQuestion q : allQuestions) {
            if (docId.equals(q.docId)) return q;
        }
        return null;
    }

    private List<Integer> shuffledOrder() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) list.add(i);
        Collections.shuffle(list);
        return list;
    }

    private List<Boolean> buildFalseBoolList() {
        List<Boolean> l = new ArrayList<>();
        for (int i = 0; i < 5; i++) l.add(false);
        return l;
    }

    private List<Integer> buildZeroIntList() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < 5; i++) l.add(0);
        return l;
    }

    private int  toInt(Object v)  { return (int) toLong(v); }
    private long toLong(Object v) {
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Boolean) return Boolean.TRUE.equals(v) ? 1L : 0L;
        return 0L;
    }


}
