package com.example.sabona;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.repository.KoZnaZnaRepository;
import com.example.sabona.repository.StatsRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ko zna zna — simultani mod (oba igrača igraju u isto vrijeme).
 *
 * Firestore dokument: gameSessions/{sessionId}/games/kzz
 * Polja:
 *   phase          : "waiting_p2" | "question" | "finished"
 *   questionIndex  : int (0–4)
 *   p1Score        : int
 *   p2Score        : int
 *   p1Answer       : int  (-1 = nije odgovorio, -2 = isteklo vrijeme)
 *   p2Answer       : int  (-1 = nije odgovorio, -2 = isteklo vrijeme)
 *   p1AnswerTime   : long (serverTimestamp u ms, 0 ako nije odgovorio)
 *   p2AnswerTime   : long (serverTimestamp u ms, 0 ako nije odgovorio)
 *   winner         : "" | "p1" | "p2" | "none"  (popunjava host kad oboje odgovore)
 *
 * Logika bodovanja (host računa):
 *   - Oba tačno → brži dobija +10
 *   - Samo jedan tačno → taj +10
 *   - Netačno → -5 (bez obzira na protivnika)
 *   - Niko ne odgovori (oba timeout) → 0 svako
 */
public class KoZnaZnaFragment extends Fragment {

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView tvQuestionNum, tvTimer, tvScore, tvInfo, tvQuestion;
    private TextView tvPlayer1Status, tvPlayer2Status, tvScore1, tvScore2;
    private Button[] answerButtons = new Button[4];
    private ProgressBar progressBar;
    private View layoutGame, layoutWaiting;
    private TextView tvWaitingMsg;

    // ── Firebase ───────────────────────────────────────────────────────────
    private final FirebaseFirestore db   = FirebaseFirestore.getInstance();
    private final FirebaseAuth      auth = FirebaseAuth.getInstance();
    private ListenerRegistration sessionListener;
    private DocumentReference    sessionRef;

    // ── Session state ──────────────────────────────────────────────────────
    private String  myUid;
    private String  sessionId;
    private boolean isHost;
    private boolean soloMode  = false;
    private boolean gameEnded = false;

    // ── Game state ─────────────────────────────────────────────────────────
    private int     currentQuestion = 0;
    private int     player1Score    = 0;
    private int     player2Score    = 0;
    private String  phase           = "waiting_p2";
    private boolean iAnswered       = false;   // da li sam JA već odgovorio na ovo pitanje
    private boolean questionDone    = false;   // da li je pitanje riješeno (host prešao dalje)
    private int     myCorrectCount     = 0;
    private int     myWrongCount       = 0;
    private boolean calculatingResult  = false; // guard protiv duplog upisa result faze

    // ── Solo state ─────────────────────────────────────────────────────────
    private int  soloP1Answer     = -1;
    private int  soloP2Answer     = -1;
    private long soloP1AnswerTime = 0;
    private long soloP2AnswerTime = 0;
    private boolean soloWaitingP2 = false;  // da li čekamo P2 odgovor u solo modu

    // ── Pitanja ────────────────────────────────────────────────────────────
    private List<KoZnaZnaRepository.Question> questions;
    private final KoZnaZnaRepository repository = new KoZnaZnaRepository();

    // ── Timer ──────────────────────────────────────────────────────────────
    private CountDownTimer timer;
    private long questionStartTime = 0;

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvQuestionNum   = view.findViewById(R.id.tvQuestionNum);
        tvTimer         = view.findViewById(R.id.tvTimer);
        tvScore         = view.findViewById(R.id.tvScore);
        tvInfo          = view.findViewById(R.id.tvInfo);
        tvQuestion      = view.findViewById(R.id.tvQuestion);
        tvPlayer1Status = view.findViewById(R.id.tvPlayer1Status);
        tvPlayer2Status = view.findViewById(R.id.tvPlayer2Status);
        tvScore1        = view.findViewById(R.id.tvScore1);
        tvScore2        = view.findViewById(R.id.tvScore2);
        progressBar     = view.findViewById(R.id.progressBar);
        layoutGame      = view.findViewById(R.id.layoutGame);
        layoutWaiting   = view.findViewById(R.id.layoutWaiting);
        tvWaitingMsg    = view.findViewById(R.id.tvWaitingMsg);

        answerButtons[0] = view.findViewById(R.id.btnAnswer1);
        answerButtons[1] = view.findViewById(R.id.btnAnswer2);
        answerButtons[2] = view.findViewById(R.id.btnAnswer3);
        answerButtons[3] = view.findViewById(R.id.btnAnswer4);

        myUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown";

        layoutGame.setVisibility(View.GONE);
        if (layoutWaiting != null) layoutWaiting.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        loadQuestions();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        if (sessionListener != null) sessionListener.remove();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Učitaj pitanja
    // ═══════════════════════════════════════════════════════════════════════

    private void loadQuestions() {
        repository.fetchQuestions(new KoZnaZnaRepository.QuestionsCallback() {
            @Override
            public void onSuccess(List<KoZnaZnaRepository.Question> loaded) {
                if (!isAdded()) return;
                Collections.shuffle(loaded);
                questions = loaded.size() > 5 ? loaded.subList(0, 5) : loaded;
                progressBar.setVisibility(View.GONE);
                showJoinDialog();
            }
            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Dialog za mod
    // ═══════════════════════════════════════════════════════════════════════

    private void showJoinDialog() {
        String suggested = myUid.length() >= 6
                ? myUid.substring(0, 6).toUpperCase() : "TEST01";

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Ko zna zna");
        builder.setMessage("Odaberi način igranja.\nKod sesije: " + suggested);

        final EditText input = new EditText(requireContext());
        input.setHint("Kod sesije");
        input.setText(suggested);
        builder.setView(input);

        builder.setPositiveButton("Kreiraj (Igrač 1)", (d, w) -> {
            sessionId = input.getText().toString().trim().toUpperCase();
            isHost    = true;
            soloMode  = false;
            createSession();
        });
        builder.setNegativeButton("Pridruži se (Igrač 2)", (d, w) -> {
            sessionId = input.getText().toString().trim().toUpperCase();
            isHost    = false;
            soloMode  = false;
            joinSession();
        });
        builder.setNeutralButton("Solo test (1 uređaj)", (d, w) -> {
            soloMode = true;
            isHost   = true;
            startSoloGame();
        });
        builder.setCancelable(false);
        builder.show();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Firestore session
    // ═══════════════════════════════════════════════════════════════════════

    private void createSession() {
        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("kzz");

        Map<String, Object> data = new HashMap<>();
        data.put("phase",         "waiting_p2");
        data.put("questionIndex", 0);
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
                    showWaiting("Čekam Igrača 2...\nKod sesije: " + sessionId);
                    startListening();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void joinSession() {
        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("kzz");

        Map<String, Object> update = new HashMap<>();
        update.put("phase",    "question");
        update.put("guestUid", myUid);

        sessionRef.update(update)
                .addOnSuccessListener(v -> {
                    showWaiting("Pridružen! Počinjemo...");
                    startListening();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Sesija nije nađena!", Toast.LENGTH_LONG).show());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Realtime listener
    // ═══════════════════════════════════════════════════════════════════════

    private void startListening() {
        sessionListener = sessionRef.addSnapshotListener((snap, err) -> {
            if (!isAdded() || snap == null || !snap.exists()) return;

            String newPhase = snap.getString("phase");
            if (newPhase == null) return;

            int  qIndex = toInt(snap.get("questionIndex"));
            player1Score = toInt(snap.get("p1Score"));
            player2Score = toInt(snap.get("p2Score"));

            switch (newPhase) {
                case "waiting_p2":
                    showWaiting("Čekam Igrača 2...\nKod: " + sessionId);
                    break;

                case "question":
                    hideWaiting();
                    if (qIndex != currentQuestion || !questionDone && qIndex > currentQuestion) {
                        // Novo pitanje (ili smo zakasnili sa lokalnim indexom)
                        currentQuestion = qIndex;
                        questionDone    = false;
                        iAnswered       = false;
                        renderQuestion();
                    } else if (qIndex == currentQuestion && !questionDone) {
                        // Isto pitanje — možda je protivnik odgovorio, osvježi status
                        int p1Ans = toInt(snap.get("p1Answer"));
                        int p2Ans = toInt(snap.get("p2Answer"));
                        updateAnswerStatus(p1Ans, p2Ans);
                        updateScoreViews();
                        // FIX: Host mora reagovati kad gost upiše odgovor/timeout.
                        // Gost ne može pozvati checkIfBothAnswered (isHost guard),
                        // pa host to radi ovdje svaki put kad snapshot stigne i ja sam već odgovorio.
                        if (isHost && iAnswered) {
                            checkIfBothAnswered();
                        }
                    }
                    break;

                case "result":
                    // Host je izračunao rezultat — prikaži i pređi na sljedeće
                    hideWaiting();
                    questionDone = true;
                    String winner = snap.getString("winner");
                    showQuestionResult(winner,
                            toInt(snap.get("p1Answer")),
                            toInt(snap.get("p2Answer")));
                    // Host nakon 2s prelazi na sljedeće pitanje
                    if (isHost) {
                        new CountDownTimer(2000, 2000) {
                            @Override public void onTick(long ms) {}
                            @Override public void onFinish() {
                                if (!isAdded()) return;
                                advanceToNextQuestion();
                            }
                        }.start();
                    }
                    break;

                case "finished":
                    showEndGame();
                    break;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Prikaz pitanja (simultano — oba igrača vide isto pitanje u isto vrijeme)
    // ═══════════════════════════════════════════════════════════════════════

    private void renderQuestion() {
        if (timer != null) timer.cancel();
        iAnswered         = false;
        questionDone      = false;
        calculatingResult = false; // reset za novo pitanje
        soloWaitingP2     = false; // reset solo stanja

        if (currentQuestion >= questions.size()) {
            if (soloMode) showEndGame();
            else if (isHost) sessionRef.update("phase", "finished");
            return;
        }

        KoZnaZnaRepository.Question q = questions.get(currentQuestion);
        tvQuestion.setText(q.question);

        for (int i = 0; i < 4; i++) {
            answerButtons[i].setText(q.answers.get(i));
            answerButtons[i].setEnabled(true);
            answerButtons[i].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
            answerButtons[i].setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.dark_blue));
        }

        tvPlayer1Status.setText("Igrač 1 ⏳");
        tvPlayer2Status.setText("Igrač 2 ⏳");
        tvQuestionNum.setText("Pitanje " + (currentQuestion + 1) + "/" + questions.size());
        tvInfo.setText("🎯 Oba igrača biraju odgovor!");

        updateScoreViews();
        questionStartTime = System.currentTimeMillis();
        startTimer();

        setupButtonClicks();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Klik na odgovor
    // ═══════════════════════════════════════════════════════════════════════

    private void onAnswerClick(int answerIndex) {
        if (iAnswered || questionDone) return;
        iAnswered = true;

        long elapsed = System.currentTimeMillis() - questionStartTime;

        for (Button btn : answerButtons) btn.setEnabled(false);

        KoZnaZnaRepository.Question q = questions.get(currentQuestion);
        boolean correct = (answerIndex == q.correctIndex);

        // Označi odgovor vizuelno odmah
        answerButtons[answerIndex].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(),
                        correct ? android.R.color.holo_green_dark : android.R.color.holo_red_light));

        // Bilježi statistiku
        if (correct) myCorrectCount++; else myWrongCount++;

        if (soloMode) {
            handleSoloAnswer(answerIndex, correct, elapsed, q);
        } else {
            // Piši u Firestore
            Map<String, Object> update = new HashMap<>();
            if (isHost) {
                update.put("p1Answer",     answerIndex);
                update.put("p1AnswerTime", elapsed);
            } else {
                update.put("p2Answer",     answerIndex);
                update.put("p2AnswerTime", elapsed);
            }
            sessionRef.update(update).addOnSuccessListener(v -> {
                // Oba igrača pozivaju ovu metodu, ali samo host (isHost==true)
                // zapravo računa — vidi guard na početku checkIfBothAnswered()
                checkIfBothAnswered();
            });
            tvInfo.setText("✅ Odgovoreno! Čekam protivnika...");
        }
    }

    // ── Host provjerava jesu li oba odgovorila i računa rezultat ──────────

    private void checkIfBothAnswered() {
        if (!isHost || questionDone || calculatingResult) return;
        calculatingResult = true; // zaključaj odmah da spriječimo dupli poziv
        sessionRef.get().addOnSuccessListener(snap -> {
            if (!isAdded() || snap == null) return;
            int p1Ans  = toInt(snap.get("p1Answer"));
            int p2Ans  = toInt(snap.get("p2Answer"));
            long p1T   = toLong(snap.get("p1AnswerTime"));
            long p2T   = toLong(snap.get("p2AnswerTime"));

            boolean p1Done = (p1Ans != -1);
            boolean p2Done = (p2Ans != -1);

            if (!p1Done || !p2Done) {
                calculatingResult = false; // još čekamo, otključaj
                return;
            }

            // Oba su odgovorila — izračunaj ko dobija bodove
            KoZnaZnaRepository.Question q = questions.get(currentQuestion);
            boolean p1Correct = (p1Ans == q.correctIndex);
            boolean p2Correct = (p2Ans == q.correctIndex);

            // Čitaj skore iz Firestore (ne lokalne var koje mogu biti zastarjele)
            int newP1Score = toInt(snap.get("p1Score"));
            int newP2Score = toInt(snap.get("p2Score"));
            String winner;

            if (p1Correct && p2Correct) {
                // Oba tačno — brži dobija +10
                if (p1T <= p2T) {
                    newP1Score += 10;
                    winner = "p1";
                } else {
                    newP2Score += 10;
                    winner = "p2";
                }
            } else if (p1Correct) {
                newP1Score += 10;
                winner = "p1";
                if (p2Ans != -2) newP2Score -= 5; // p2 je odgovorio netačno (ne timeout)
            } else if (p2Correct) {
                newP2Score += 10;
                winner = "p2";
                if (p1Ans != -2) newP1Score -= 5;
            } else {
                // Niko nije tačno odgovorio
                if (p1Ans != -2) newP1Score -= 5;
                if (p2Ans != -2) newP2Score -= 5;
                winner = "none";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("phase",    "result");
            result.put("p1Score",  newP1Score);
            result.put("p2Score",  newP2Score);
            result.put("winner",   winner);
            sessionRef.update(result);
        });
    }

    // ── Prikaži rezultat pitanja ───────────────────────────────────────────

    private void showQuestionResult(String winner, int p1Ans, int p2Ans) {
        if (timer != null) timer.cancel();
        for (Button btn : answerButtons) btn.setEnabled(false);

        if (currentQuestion < questions.size()) {
            int correct = questions.get(currentQuestion).correctIndex;
            markCorrectAnswer(correct);
        }

        updateAnswerStatus(p1Ans, p2Ans);
        updateScoreViews();

        String msg;
        if ("p1".equals(winner))   msg = "🏆 Igrač 1 osvaja bodove!";
        else if ("p2".equals(winner)) msg = "🏆 Igrač 2 osvaja bodove!";
        else                          msg = "❌ Niko nije tačno odgovorio.";
        tvInfo.setText(msg);
    }

    private void updateAnswerStatus(int p1Ans, int p2Ans) {
        if (p1Ans == -1) {
            tvPlayer1Status.setText("Igrač 1 ⏳");
        } else if (p1Ans == -2) {
            tvPlayer1Status.setText("Igrač 1 ⌛");
        } else {
            KoZnaZnaRepository.Question q = questions.get(currentQuestion);
            tvPlayer1Status.setText(p1Ans == q.correctIndex ? "Igrač 1 ✅" : "Igrač 1 ❌");
        }

        if (p2Ans == -1) {
            tvPlayer2Status.setText("Igrač 2 ⏳");
        } else if (p2Ans == -2) {
            tvPlayer2Status.setText("Igrač 2 ⌛");
        } else {
            KoZnaZnaRepository.Question q = questions.get(currentQuestion);
            tvPlayer2Status.setText(p2Ans == q.correctIndex ? "Igrač 2 ✅" : "Igrač 2 ❌");
        }
    }

    // ── Host prelazi na sljedeće pitanje ──────────────────────────────────

    private void advanceToNextQuestion() {
        if (!isHost) return;
        int nextQ = currentQuestion + 1;
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

    // ═══════════════════════════════════════════════════════════════════════
    // Timer (5 sekundi po pitanju)
    // ═══════════════════════════════════════════════════════════════════════

    private void startTimer() {
        tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.petal));
        timer = new CountDownTimer(5000, 1000) {
            @Override public void onTick(long ms) {
                long s = ms / 1000;
                tvTimer.setText(String.valueOf(s));
                if (s <= 2)
                    tvTimer.setTextColor(
                            ContextCompat.getColor(requireContext(), android.R.color.holo_red_light));
            }
            @Override public void onFinish() {
                if (!isAdded() || questionDone) return;
                tvTimer.setText("0");
                if (!iAnswered) {
                    // Označimo timeout kao -2
                    iAnswered = true;
                    for (Button btn : answerButtons) btn.setEnabled(false);
                    if (soloMode) {
                        handleSoloTimeout();
                    } else {
                        Map<String, Object> update = new HashMap<>();
                        if (isHost) update.put("p1Answer", -2);
                        else        update.put("p2Answer", -2);
                        update.put(isHost ? "p1AnswerTime" : "p2AnswerTime", 99999L);
                        sessionRef.update(update).addOnSuccessListener(v -> checkIfBothAnswered());
                    }
                }
                // NAPOMENA: Guest sam upisuje vlastiti timeout kad mu istekne tajmer.
                // Host NE smije ovdje pisati p2Answer=-2, jer guest-ov tajmer još uvijek
                // može biti aktivan na drugom uređaju i on sam će upisati -2 kad istekne.
            }
        }.start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Solo mod
    // ═══════════════════════════════════════════════════════════════════════

    private void startSoloGame() {
        currentQuestion = 0;
        player1Score    = 0;
        player2Score    = 0;
        hideWaiting();
        renderQuestion();
    }

    private void handleSoloAnswer(int answerIndex, boolean correct, long elapsed,
                                  KoZnaZnaRepository.Question q) {
        if (!soloWaitingP2) {
            // Igrač 1 odgovorio
            soloP1Answer     = answerIndex;
            soloP1AnswerTime = elapsed;
            soloWaitingP2    = true;

            tvPlayer1Status.setText(correct ? "Igrač 1 ✅" : "Igrač 1 ❌");
            tvInfo.setText("🎯 Igrač 2 bira odgovor!");

            // Reaktiviraj dugmad za Igrača 2 (osim kliknutog ako netačno)
            for (int i = 0; i < 4; i++) {
                answerButtons[i].setEnabled(correct ? false : (i != answerIndex));
            }
            if (correct) {
                // P1 tačno odmah — P2 nema šansu (kao u specifikaciji: brži dobija)
                soloP2Answer = -1;
                soloFinishQuestion(q);
            }
        } else {
            // Igrač 2 odgovorio
            soloP2Answer     = answerIndex;
            soloP2AnswerTime = elapsed;
            soloWaitingP2    = false;
            for (Button btn : answerButtons) btn.setEnabled(false);
            tvPlayer2Status.setText(correct ? "Igrač 2 ✅" : "Igrač 2 ❌");
            soloFinishQuestion(q);
        }
    }

    private void handleSoloTimeout() {
        if (!soloWaitingP2) {
            // P1 nije odgovorio
            soloP1Answer     = -2;
            soloP1AnswerTime = 99999;
            soloWaitingP2    = true;
            tvPlayer1Status.setText("Igrač 1 ⌛");
            tvInfo.setText("🎯 Igrač 2 bira odgovor!");
            // Reaktiviraj za P2
            for (Button btn : answerButtons) btn.setEnabled(true);
        } else {
            // P2 nije odgovorio
            soloP2Answer = -2;
            soloWaitingP2 = false;
            tvPlayer2Status.setText("Igrač 2 ⌛");
            soloFinishQuestion(questions.get(currentQuestion));
        }
    }

    private void soloFinishQuestion(KoZnaZnaRepository.Question q) {
        for (Button btn : answerButtons) btn.setEnabled(false);
        markCorrectAnswer(q.correctIndex);

        boolean p1Correct = (soloP1Answer == q.correctIndex);
        boolean p2Correct = (soloP2Answer == q.correctIndex);

        if (p1Correct && p2Correct) {
            if (soloP1AnswerTime <= soloP2AnswerTime) { player1Score += 10; tvInfo.setText("🏆 Igrač 1 bio brži! +10"); }
            else                                       { player2Score += 10; tvInfo.setText("🏆 Igrač 2 bio brži! +10"); }
        } else if (p1Correct) {
            player1Score += 10;
            if (soloP2Answer != -2) player2Score -= 5;
            tvInfo.setText("🏆 Igrač 1 tačno! +10");
        } else if (p2Correct) {
            player2Score += 10;
            if (soloP1Answer != -2) player1Score -= 5;
            tvInfo.setText("🏆 Igrač 2 tačno! +10");
        } else {
            if (soloP1Answer != -2) player1Score -= 5;
            if (soloP2Answer != -2) player2Score -= 5;
            tvInfo.setText("❌ Niko nije tačno odgovorio.");
        }

        updateScoreViews();

        // Reset solo state
        soloP1Answer = soloP2Answer = -1;
        soloP1AnswerTime = soloP2AnswerTime = 0;

        new CountDownTimer(2000, 2000) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() {
                if (!isAdded()) return;
                currentQuestion++;
                if (currentQuestion < questions.size()) renderQuestion();
                else showEndGame();
            }
        }.start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Kraj igre
    // ═══════════════════════════════════════════════════════════════════════

    private void showEndGame() {
        if (gameEnded) return;
        gameEnded = true;
        if (timer != null) timer.cancel();
        if (!isAdded()) return;

        int myScore = isHost ? player1Score : player2Score;
        new StatsRepository().saveKoZnaZnaResult(myScore, myCorrectCount, myWrongCount);

        String winner;
        if (player1Score > player2Score)
            winner = "Pobjednik: Igrač 1! (" + player1Score + " : " + player2Score + ")";
        else if (player2Score > player1Score)
            winner = "Pobjednik: Igrač 2! (" + player1Score + " : " + player2Score + ")";
        else
            winner = "Nerješeno! (" + player1Score + " : " + player2Score + ")";

        Toast.makeText(requireContext(), winner, Toast.LENGTH_LONG).show();
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_kozna_to_spojnice);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private void showWaiting(String msg) {
        if (layoutWaiting != null) {
            layoutWaiting.setVisibility(View.VISIBLE);
            if (tvWaitingMsg != null) tvWaitingMsg.setText(msg);
        }
        layoutGame.setVisibility(View.GONE);
    }

    private void hideWaiting() {
        if (layoutWaiting != null) layoutWaiting.setVisibility(View.GONE);
        layoutGame.setVisibility(View.VISIBLE);
        setupButtonClicks();
    }

    private void setupButtonClicks() {
        for (int i = 0; i < 4; i++) {
            int idx = i;
            answerButtons[i].setOnClickListener(v -> onAnswerClick(idx));
        }
    }

    private void markCorrectAnswer(int idx) {
        if (idx < 0 || idx >= answerButtons.length) return;
        answerButtons[idx].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
        answerButtons[idx].setTextColor(
                ContextCompat.getColor(requireContext(), R.color.white));
    }

    private void updateScoreViews() {
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
        tvScore1.setText(player1Score + " bod.");
        tvScore2.setText(player2Score + " bod.");
    }

    private int  toInt(Object v)  { return (int) toLong(v); }
    private long toLong(Object v) {
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        return 0L;
    }
}