package com.example.sabona.koznazna;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.repository.StatsRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel za Ko Zna Zna.
 *
 * Izvučena logika iz KoZnaZnaFragment — Fragment ostaje samo prezentacijski sloj
 * (Views + observeri), dok sva logika igre, Firestore komunikacija i stanje žive ovdje.
 *
 * LiveData koje Fragment observuje:
 *   phase          → šta da prikaže (LOADING, WAITING_P2, QUESTION, RESULT, GAME_OVER)
 *   currentQuestion → index trenutnog pitanja (0–4)
 *   timerText      → "00:05", "00:04"... za TextView
 *   timerWarning   → true kad je <= 2s (Fragment mijenja boju)
 *   player1Score   → bodovi igrača 1
 *   player2Score   → bodovi igrača 2
 *   p1Status       → "Igrač 1 ✅ / ❌ / ⏳ / ⌛"
 *   p2Status       → "Igrač 2 ✅ / ❌ / ⏳ / ⌛"
 *   infoText       → poruka na dnu ekrana
 *   questionEvent  → one-shot event kad treba renderovati novo pitanje
 *   resultEvent    → one-shot event (winner string) kad treba prikazati rezultat pitanja
 *   gameOverEvent  → one-shot event kad je igra završena
 *   answerFeedback → index dugmeta i je li tačan (za koloriranje), -1 = reset
 *   correctIndex   → index tačnog odgovora (prikazati na kraju pitanja)
 */
public class KoZnaZnaViewModel extends ViewModel {

    // ── Faze igre ──────────────────────────────────────────────────────────
    public enum Phase {
        LOADING,
        WAITING_P2,
        QUESTION,
        RESULT,
        GAME_OVER
    }

    // ── One-shot event wrapper (da se event ne okine ponovo na rotate) ──────
    public static class Event<T> {
        private T content;
        private boolean handled = false;

        public Event(T content) { this.content = content; }

        /** Vrati sadržaj samo prvi put, zatim null */
        public T getIfNotHandled() {
            if (handled) return null;
            handled = true;
            return content;
        }

        /** Uvijek vrati sadržaj (za peek bez konzumiranja) */
        public T peek() { return content; }
    }

    // ── Wrapper za feedback odgovora ────────────────────────────────────────
    public static class AnswerFeedback {
        public final int  index;
        public final boolean correct;
        public AnswerFeedback(int index, boolean correct) {
            this.index   = index;
            this.correct = correct;
        }
    }

    // ── LiveData ────────────────────────────────────────────────────────────
    private final MutableLiveData<Phase>          phase           = new MutableLiveData<>(Phase.LOADING);
    private final MutableLiveData<String>         timerText       = new MutableLiveData<>("00:05");
    private final MutableLiveData<Boolean>        timerWarning    = new MutableLiveData<>(false);
    private final MutableLiveData<Integer>        player1Score    = new MutableLiveData<>(0);
    private final MutableLiveData<Integer>        player2Score    = new MutableLiveData<>(0);
    private final MutableLiveData<String>         p1Status        = new MutableLiveData<>("Igrač 1 ⏳");
    private final MutableLiveData<String>         p2Status        = new MutableLiveData<>("Igrač 2 ⏳");
    private final MutableLiveData<String>         infoText        = new MutableLiveData<>("");
    private final MutableLiveData<String>         waitingMsg      = new MutableLiveData<>("");
    private final MutableLiveData<Event<Integer>> questionEvent   = new MutableLiveData<>(); // int = questionIndex
    private final MutableLiveData<Event<String>>  resultEvent     = new MutableLiveData<>(); // String = winner
    private final MutableLiveData<Event<String>>  gameOverEvent   = new MutableLiveData<>();
    private final MutableLiveData<AnswerFeedback> answerFeedback  = new MutableLiveData<>();
    private final MutableLiveData<Integer>        correctIndex    = new MutableLiveData<>(-1);

    // Getteri za LiveData
    public LiveData<Phase>          getPhase()          { return phase; }
    public LiveData<String>         getTimerText()      { return timerText; }
    public LiveData<Boolean>        getTimerWarning()   { return timerWarning; }
    public LiveData<Integer>        getPlayer1Score()   { return player1Score; }
    public LiveData<Integer>        getPlayer2Score()   { return player2Score; }
    public LiveData<String>         getP1Status()       { return p1Status; }
    public LiveData<String>         getP2Status()       { return p2Status; }
    public LiveData<String>         getInfoText()       { return infoText; }
    public LiveData<String>         getWaitingMsg()     { return waitingMsg; }
    public LiveData<Event<Integer>> getQuestionEvent()  { return questionEvent; }
    public LiveData<Event<String>>  getResultEvent()    { return resultEvent; }
    public LiveData<Event<String>>  getGameOverEvent()  { return gameOverEvent; }
    public LiveData<AnswerFeedback> getAnswerFeedback() { return answerFeedback; }
    public LiveData<Integer>        getCorrectIndex()   { return correctIndex; }

    // ── Firebase ────────────────────────────────────────────────────────────
    private final FirebaseFirestore db   = FirebaseFirestore.getInstance();
    private final FirebaseAuth      auth = FirebaseAuth.getInstance();
    private ListenerRegistration sessionListener;
    private DocumentReference    sessionRef;

    // ── Session state ────────────────────────────────────────────────────────
    private String  myUid;
    private String  sessionId;
    private boolean isHost;
    private boolean soloMode  = false;
    private boolean gameEnded = false;

    // ── Pitanja ──────────────────────────────────────────────────────────────
    private List<KoZnaZnaRepository.Question> allQuestions;
    private List<String>                      orderedQuestionIds = new ArrayList<>();
    private List<KoZnaZnaRepository.Question> questions          = new ArrayList<>();

    private final KoZnaZnaRepository repository = new KoZnaZnaRepository();

    // ── Game state ───────────────────────────────────────────────────────────
    private int     currentQuestionIndex = 0;
    private int     p1Score              = 0;
    private int     p2Score              = 0;
    private String  firestorePhase       = "waiting_p2";
    private boolean iAnswered            = false;
    private boolean questionDone         = false;
    private int     myCorrectCount       = 0;
    private int     myWrongCount         = 0;
    private boolean calculatingResult    = false;
    private int     lastRenderedQIndex   = -1;

    // ── Solo state ────────────────────────────────────────────────────────────
    private int     soloP1Answer     = -1;
    private int     soloP2Answer     = -1;
    private long    soloP1AnswerTime = 0;
    private long    soloP2AnswerTime = 0;
    private boolean soloWaitingP2    = false;

    // ── Timer ─────────────────────────────────────────────────────────────────
    private CountDownTimer timer;
    private long questionStartTime = 0;

    // ═════════════════════════════════════════════════════════════════════════
    // Init
    // ═════════════════════════════════════════════════════════════════════════

    public void init(String uid) {
        if (myUid != null) return; // već inicijalizovan (rotation guard)
        myUid = uid != null ? uid : "unknown";
        loadQuestions();
    }

    private void loadQuestions() {
        phase.setValue(Phase.LOADING);
        repository.fetchQuestionsWithIds(new KoZnaZnaRepository.QuestionsWithIdsCallback() {
            @Override
            public void onSuccess(List<KoZnaZnaRepository.Question> loaded) {
                allQuestions = loaded;
                phase.setValue(Phase.WAITING_P2); // Signal Fragmentu da prikaže dialog
            }
            @Override
            public void onError(Exception e) {
                infoText.setValue("Greška učitavanja: " + e.getMessage());
            }
        });
    }

    /** Vraća myUid da Fragment može predložiti kod sesije */
    public String getMyUid() { return myUid != null ? myUid : "unknown"; }

    /** Vraća pitanje za trenutni index (Fragment čita text i odgovore) */
    public KoZnaZnaRepository.Question getCurrentQuestionData() {
        if (questions.isEmpty() || currentQuestionIndex >= questions.size()) return null;
        return questions.get(currentQuestionIndex);
    }

    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public int getTotalQuestions()       { return questions.size(); }
    public boolean isSoloMode()          { return soloMode; }
    public boolean isSoloWaitingP2()     { return soloWaitingP2; }

    // ═════════════════════════════════════════════════════════════════════════
    // Akcije koje Fragment poziva
    // ═════════════════════════════════════════════════════════════════════════

    public void createSession(String sid) {
        sessionId = sid;
        isHost    = true;
        soloMode  = false;

        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("kzz");

        // Host miješa pitanja i upisuje redosled u Firestore
        List<KoZnaZnaRepository.Question> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        List<KoZnaZnaRepository.Question> picked = shuffled.size() > 5
                ? shuffled.subList(0, 5) : shuffled;

        List<String> ids = new ArrayList<>();
        for (KoZnaZnaRepository.Question q : picked) {
            if (q.docId != null) ids.add(q.docId);
        }
        orderedQuestionIds = ids;
        questions = new ArrayList<>(picked);

        Map<String, Object> data = new HashMap<>();
        data.put("phase",         "waiting_p2");
        data.put("questionIndex", 0);
        data.put("questionIds",   ids);
        data.put("p1Score",       0);
        data.put("p2Score",       0);
        data.put("p1Answer",      -1);
        data.put("p2Answer",      -1);
        data.put("p1AnswerTime",  0L);
        data.put("p2AnswerTime",  0L);
        data.put("winner",        "");
        data.put("hostUid",       myUid);

        sessionRef.set(data)
                .addOnSuccessListener(v -> {
                    waitingMsg.setValue("Čekam Igrača 2...\nKod sesije: " + sessionId);
                    phase.setValue(Phase.WAITING_P2);
                    startListening();
                })
                .addOnFailureListener(e ->
                        infoText.setValue("Greška: " + e.getMessage()));
    }

    public void joinSession(String sid) {
        sessionId = sid;
        isHost    = false;
        soloMode  = false;

        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("kzz");

        // Guest čita sesiju da dobije questionIds od hosta
        sessionRef.get().addOnSuccessListener(snap -> {
            if (!snap.exists()) {
                infoText.setValue("Sesija nije nađena!");
                return;
            }

            List<?> rawIds = (List<?>) snap.get("questionIds");
            if (rawIds != null) {
                orderedQuestionIds.clear();
                for (Object o : rawIds) orderedQuestionIds.add(String.valueOf(o));
            }

            if (!buildOrderedQuestions()) {
                infoText.setValue("Greška mapiranja pitanja!");
                return;
            }

            Map<String, Object> update = new HashMap<>();
            update.put("phase",    "question");
            update.put("guestUid", myUid);

            sessionRef.update(update)
                    .addOnSuccessListener(v -> {
                        waitingMsg.setValue("Pridružen! Počinjemo...");
                        phase.setValue(Phase.WAITING_P2);
                        startListening();
                    })
                    .addOnFailureListener(e ->
                            infoText.setValue("Greška: " + e.getMessage()));

        }).addOnFailureListener(e -> infoText.setValue("Sesija nije nađena!"));
    }

    public void startSoloGame() {
        soloMode = true;
        isHost   = true;

        List<KoZnaZnaRepository.Question> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        questions = shuffled.size() > 5 ? new ArrayList<>(shuffled.subList(0, 5)) : shuffled;

        currentQuestionIndex = 0;
        p1Score              = 0;
        p2Score              = 0;
        lastRenderedQIndex   = -1;
        player1Score.setValue(0);
        player2Score.setValue(0);

        renderQuestion();
    }

    /** Poziva Fragment kad korisnik klikne na odgovor */
    public void onAnswerClick(int answerIndex) {
        if (iAnswered || questionDone) return;
        iAnswered = true;

        long elapsed = System.currentTimeMillis() - questionStartTime;

        KoZnaZnaRepository.Question q = questions.get(currentQuestionIndex);
        boolean correct = (answerIndex == q.correctIndex);

        answerFeedback.setValue(new AnswerFeedback(answerIndex, correct));

        if (correct) myCorrectCount++; else myWrongCount++;

        if (soloMode) {
            handleSoloAnswer(answerIndex, correct, elapsed, q);
        } else {
            Map<String, Object> update = new HashMap<>();
            if (isHost) {
                update.put("p1Answer",     answerIndex);
                update.put("p1AnswerTime", elapsed);
            } else {
                update.put("p2Answer",     answerIndex);
                update.put("p2AnswerTime", elapsed);
            }
            sessionRef.update(update).addOnSuccessListener(v -> {
                if (isHost) checkIfBothAnswered();
            });
            infoText.setValue("✅ Odgovoreno! Čekam protivnika...");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Realtime listener
    // ═════════════════════════════════════════════════════════════════════════

    private void startListening() {
        sessionListener = sessionRef.addSnapshotListener((snap, err) -> {
            if (snap == null || !snap.exists()) return;

            String newPhase = snap.getString("phase");
            if (newPhase == null) return;

            int qIndex = toInt(snap.get("questionIndex"));
            p1Score    = toInt(snap.get("p1Score"));
            p2Score    = toInt(snap.get("p2Score"));
            player1Score.setValue(p1Score);
            player2Score.setValue(p2Score);

            switch (newPhase) {
                case "waiting_p2":
                    waitingMsg.setValue("Čekam Igrača 2...\nKod: " + sessionId);
                    phase.setValue(Phase.WAITING_P2);
                    break;

                case "question":
                    if (qIndex != lastRenderedQIndex) {
                        lastRenderedQIndex   = qIndex;
                        currentQuestionIndex = qIndex;
                        questionDone         = false;
                        iAnswered            = false;
                        calculatingResult    = false;
                        renderQuestion();
                    } else {
                        // Isto pitanje — samo osvježi status
                        int p1Ans = toInt(snap.get("p1Answer"));
                        int p2Ans = toInt(snap.get("p2Answer"));
                        updateAnswerStatus(p1Ans, p2Ans);
                        if (isHost && iAnswered && !questionDone) {
                            checkIfBothAnswered();
                        }
                    }
                    break;

                case "result":
                    if (!questionDone) {
                        questionDone = true;
                        if (timer != null) timer.cancel();
                        String winner = snap.getString("winner");
                        int p1Ans = toInt(snap.get("p1Answer"));
                        int p2Ans = toInt(snap.get("p2Answer"));
                        showQuestionResult(winner, p1Ans, p2Ans);
                        if (isHost) {
                            new CountDownTimer(2000, 2000) {
                                @Override public void onTick(long ms) {}
                                @Override public void onFinish() { advanceToNextQuestion(); }
                            }.start();
                        }
                    }
                    break;

                case "finished":
                    triggerGameOver();
                    break;
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Renderuj pitanje
    // ═════════════════════════════════════════════════════════════════════════

    private void renderQuestion() {
        if (timer != null) timer.cancel();
        iAnswered         = false;
        questionDone      = false;
        calculatingResult = false;
        soloWaitingP2     = false;

        if (currentQuestionIndex >= questions.size()) {
            if (soloMode) triggerGameOver();
            else if (isHost) sessionRef.update("phase", "finished");
            return;
        }

        p1Status.setValue("Igrač 1 ⏳");
        p2Status.setValue("Igrač 2 ⏳");
        infoText.setValue("🎯 Oba igrača biraju odgovor!");
        correctIndex.setValue(-1);
        answerFeedback.setValue(null);

        phase.setValue(Phase.QUESTION);
        questionEvent.setValue(new Event<>(currentQuestionIndex));

        questionStartTime = System.currentTimeMillis();
        startTimer();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Timer
    // ═════════════════════════════════════════════════════════════════════════

    private void startTimer() {
        timerWarning.setValue(false);
        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long ms) {
                long s = ms / 1000 + 1;
                timerText.setValue(String.format("00:%02d", s));
                if (s <= 2) timerWarning.setValue(true);
            }

            @Override
            public void onFinish() {
                timerText.setValue("00:00");
                if (!iAnswered && !questionDone) {
                    iAnswered = true;
                    if (soloMode) {
                        handleSoloTimeout();
                    } else {
                        Map<String, Object> update = new HashMap<>();
                        String answerField = isHost ? "p1Answer" : "p2Answer";
                        String timeField   = isHost ? "p1AnswerTime" : "p2AnswerTime";
                        update.put(answerField, -2);
                        update.put(timeField,   99999L);
                        sessionRef.update(update)
                                .addOnSuccessListener(v -> {
                                    if (isHost) checkIfBothAnswered();
                                });
                        infoText.setValue("⌛ Vrijeme isteklo!");
                    }
                }
            }
        }.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Host — računaj rezultat
    // ═════════════════════════════════════════════════════════════════════════

    private void checkIfBothAnswered() {
        if (!isHost || questionDone || calculatingResult) return;
        calculatingResult = true;

        sessionRef.get().addOnSuccessListener(snap -> {
            if (snap == null) return;

            int  p1Ans = toInt(snap.get("p1Answer"));
            int  p2Ans = toInt(snap.get("p2Answer"));
            long p1T   = toLong(snap.get("p1AnswerTime"));
            long p2T   = toLong(snap.get("p2AnswerTime"));

            boolean p1Done = (p1Ans != -1);
            boolean p2Done = (p2Ans != -1);

            if (!p1Done || !p2Done) {
                calculatingResult = false;
                return;
            }

            KoZnaZnaRepository.Question q = questions.get(currentQuestionIndex);
            boolean p1Correct = (p1Ans != -2) && (p1Ans == q.correctIndex);
            boolean p2Correct = (p2Ans != -2) && (p2Ans == q.correctIndex);

            int newP1Score = toInt(snap.get("p1Score"));
            int newP2Score = toInt(snap.get("p2Score"));
            String winner;

            if (p1Correct && p2Correct) {
                // Oba tačno — samo brži +10, sporiji 0 (nema -5!)
                if (p1T <= p2T) { newP1Score += 10; winner = "p1"; }
                else             { newP2Score += 10; winner = "p2"; }
            } else if (p1Correct) {
                newP1Score += 10;
                if (p2Ans != -2) newP2Score -= 5;
                winner = "p1";
            } else if (p2Correct) {
                newP2Score += 10;
                if (p1Ans != -2) newP1Score -= 5;
                winner = "p2";
            } else {
                if (p1Ans != -2) newP1Score -= 5;
                if (p2Ans != -2) newP2Score -= 5;
                winner = "none";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("phase",   "result");
            result.put("p1Score", newP1Score);
            result.put("p2Score", newP2Score);
            result.put("winner",  winner);
            sessionRef.update(result);
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Prikaži rezultat pitanja
    // ═════════════════════════════════════════════════════════════════════════

    private void showQuestionResult(String winner, int p1Ans, int p2Ans) {
        if (timer != null) timer.cancel();

        if (currentQuestionIndex < questions.size()) {
            correctIndex.setValue(questions.get(currentQuestionIndex).correctIndex);
        }

        updateAnswerStatus(p1Ans, p2Ans);

        String msg;
        if ("p1".equals(winner))   msg = "🏆 Igrač 1 osvaja bodove!";
        else if ("p2".equals(winner)) msg = "🏆 Igrač 2 osvaja bodove!";
        else                          msg = "❌ Niko nije tačno odgovorio.";
        infoText.setValue(msg);

        phase.setValue(Phase.RESULT);
        resultEvent.setValue(new Event<>(winner));
    }

    private void updateAnswerStatus(int p1Ans, int p2Ans) {
        if (questions.isEmpty() || currentQuestionIndex >= questions.size()) return;
        KoZnaZnaRepository.Question q = questions.get(currentQuestionIndex);

        p1Status.setValue(p1Ans == -1 ? "Igrač 1 ⏳"
                : p1Ans == -2         ? "Igrač 1 ⌛"
                  : p1Ans == q.correctIndex ? "Igrač 1 ✅" : "Igrač 1 ❌");

        p2Status.setValue(p2Ans == -1 ? "Igrač 2 ⏳"
                : p2Ans == -2         ? "Igrač 2 ⌛"
                  : p2Ans == q.correctIndex ? "Igrač 2 ✅" : "Igrač 2 ❌");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Host napreduje na sljedeće pitanje
    // ═════════════════════════════════════════════════════════════════════════

    private void advanceToNextQuestion() {
        if (!isHost) return;
        int nextQ = currentQuestionIndex + 1;
        Map<String, Object> update = new HashMap<>();
        update.put("questionIndex", nextQ);
        update.put("phase",         nextQ < questions.size() ? "question" : "finished");
        update.put("p1Answer",      -1);
        update.put("p2Answer",      -1);
        update.put("p1AnswerTime",  0L);
        update.put("p2AnswerTime",  0L);
        update.put("winner",        "");
        sessionRef.update(update);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Solo mod
    // ═════════════════════════════════════════════════════════════════════════

    private void handleSoloAnswer(int answerIndex, boolean correct, long elapsed,
                                  KoZnaZnaRepository.Question q) {
        if (!soloWaitingP2) {
            soloP1Answer     = answerIndex;
            soloP1AnswerTime = elapsed;
            soloWaitingP2    = true;

            p1Status.setValue(correct ? "Igrač 1 ✅" : "Igrač 1 ❌");
            infoText.setValue(correct
                    ? "🎯 Igrač 1 tačno! Igrač 2, tvoj red!"
                    : "🎯 Igrač 2 bira odgovor!");
            // Fragment treba da reaguje na soloWaitingP2=true i omogući dugmad za P2
            questionEvent.setValue(new Event<>(currentQuestionIndex)); // re-signal za P2 turn
        } else {
            soloP2Answer     = answerIndex;
            soloP2AnswerTime = elapsed;
            soloWaitingP2    = false;
            p2Status.setValue(correct ? "Igrač 2 ✅" : "Igrač 2 ❌");
            soloFinishQuestion(q);
        }
    }

    private void handleSoloTimeout() {
        KoZnaZnaRepository.Question q = questions.get(currentQuestionIndex);
        if (!soloWaitingP2) {
            soloP1Answer     = -2;
            soloP1AnswerTime = 99999;
            soloWaitingP2    = true;
            p1Status.setValue("Igrač 1 ⌛");
            infoText.setValue("🎯 Igrač 2 bira odgovor!");
            // Fragment ponovo omogući dugmad i restart timer za P2
            questionEvent.setValue(new Event<>(currentQuestionIndex));
            startTimer();
        } else {
            soloP2Answer  = -2;
            soloWaitingP2 = false;
            p2Status.setValue("Igrač 2 ⌛");
            soloFinishQuestion(q);
        }
    }

    private void soloFinishQuestion(KoZnaZnaRepository.Question q) {
        if (timer != null) timer.cancel();

        boolean p1Correct = (soloP1Answer != -2) && (soloP1Answer == q.correctIndex);
        boolean p2Correct = (soloP2Answer != -2) && (soloP2Answer == q.correctIndex);

        if (p1Correct && p2Correct) {
            if (soloP1AnswerTime <= soloP2AnswerTime) { p1Score += 10; infoText.setValue("🏆 Igrač 1 bio brži! +10"); }
            else                                       { p2Score += 10; infoText.setValue("🏆 Igrač 2 bio brži! +10"); }
        } else if (p1Correct) {
            p1Score += 10;
            if (soloP2Answer != -2) p2Score -= 5;
            infoText.setValue("🏆 Igrač 1 tačno! +10");
        } else if (p2Correct) {
            p2Score += 10;
            if (soloP1Answer != -2) p1Score -= 5;
            infoText.setValue("🏆 Igrač 2 tačno! +10");
        } else {
            if (soloP1Answer != -2) p1Score -= 5;
            if (soloP2Answer != -2) p2Score -= 5;
            infoText.setValue("❌ Niko nije tačno odgovorio.");
        }

        correctIndex.setValue(q.correctIndex);
        player1Score.setValue(p1Score);
        player2Score.setValue(p2Score);
        phase.setValue(Phase.RESULT);
        resultEvent.setValue(new Event<>("solo_done"));

        soloP1Answer = soloP2Answer = -1;
        soloP1AnswerTime = soloP2AnswerTime = 0;

        new CountDownTimer(2000, 2000) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() {
                currentQuestionIndex++;
                lastRenderedQIndex = -1;
                if (currentQuestionIndex < questions.size()) renderQuestion();
                else triggerGameOver();
            }
        }.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Kraj igre
    // ═════════════════════════════════════════════════════════════════════════

    private void triggerGameOver() {
        if (gameEnded) return;
        gameEnded = true;
        if (timer != null) timer.cancel();

        int myScore = isHost ? p1Score : p2Score;
        new StatsRepository().saveKoZnaZnaResult(myScore, myCorrectCount, myWrongCount);

        String summary;
        if (p1Score > p2Score)
            summary = "Pobjednik: Igrač 1! (" + p1Score + " : " + p2Score + ")";
        else if (p2Score > p1Score)
            summary = "Pobjednik: Igrač 2! (" + p1Score + " : " + p2Score + ")";
        else
            summary = "Nerješeno! (" + p1Score + " : " + p2Score + ")";

        phase.setValue(Phase.GAME_OVER);
        gameOverEvent.setValue(new Event<>(summary));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Session info za navigation
    // ═════════════════════════════════════════════════════════════════════════

    public String getSessionId() { return soloMode ? "" : sessionId; }
    public boolean isHost()      { return isHost; }

    // ═════════════════════════════════════════════════════════════════════════
    // Cleanup
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCleared() {
        super.onCleared();
        if (timer != null) timer.cancel();
        if (sessionListener != null) sessionListener.remove();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private boolean buildOrderedQuestions() {
        if (allQuestions == null || orderedQuestionIds.isEmpty()) return false;
        Map<String, KoZnaZnaRepository.Question> idMap = new HashMap<>();
        for (KoZnaZnaRepository.Question q : allQuestions) {
            if (q.docId != null) idMap.put(q.docId, q);
        }
        questions.clear();
        for (String id : orderedQuestionIds) {
            KoZnaZnaRepository.Question q = idMap.get(id);
            if (q != null) questions.add(q);
        }
        return !questions.isEmpty();
    }

    private int  toInt(Object v)  { return (int) toLong(v); }
    private long toLong(Object v) {
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        return 0L;
    }
}