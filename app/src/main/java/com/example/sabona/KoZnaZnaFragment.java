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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoZnaZnaFragment extends Fragment {

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView tvQuestionNum, tvTimer, tvScore, tvInfo, tvQuestion;
    private TextView tvPlayer1Status, tvPlayer2Status, tvScore1, tvScore2;
    private Button[] answerButtons = new Button[4];
    private ProgressBar progressBar;
    private View layoutGame, layoutWaiting;
    private TextView tvWaitingMsg;

    // ── Firebase ───────────────────────────────────────────────────────────
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth auth = FirebaseAuth.getInstance();
    private ListenerRegistration sessionListener;
    private DocumentReference sessionRef;

    // ── Session state ──────────────────────────────────────────────────────
    private String myUid;
    private String sessionId;
    private boolean isHost;
    private String myRole;
    private boolean soloMode = false;

    // ── Game state ─────────────────────────────────────────────────────────
    private int currentQuestion = 0;
    private int player1Score = 0;
    private int player2Score = 0;
    private String phase = "p1_turn";
    private boolean questionFinished = false;
    private boolean myTurn = false;
    private int myCorrectCount = 0;
    private int myWrongCount = 0;

    // ── Pitanja ────────────────────────────────────────────────────────────
    private List<KoZnaZnaRepository.Question> questions;
    private final KoZnaZnaRepository repository = new KoZnaZnaRepository();

    // ── Timer ──────────────────────────────────────────────────────────────
    private CountDownTimer timer;

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

    // ── Učitaj pitanja iz Firestore ────────────────────────────────────────
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
                Toast.makeText(requireContext(),
                        "Greška pri učitavanju pitanja: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Dialog za odabir moda ─────────────────────────────────────────────
    private void showJoinDialog() {
        String suggested = myUid.length() >= 6
                ? myUid.substring(0, 6).toUpperCase() : "TEST01";

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Ko zna zna");
        builder.setMessage("Odaberi način igranja.\nKod sesije za 2 uređaja: " + suggested);

        final EditText input = new EditText(requireContext());
        input.setHint("Kod sesije");
        input.setText(suggested);
        builder.setView(input);

        // Igrač 1 – kreira sesiju (2 uređaja)
        builder.setPositiveButton("Kreiraj (Igrač 1)", (d, w) -> {
            sessionId = input.getText().toString().trim().toUpperCase();
            isHost = true;
            myRole = "p1";
            soloMode = false;
            createSession();
        });

        // Igrač 2 – pridružuje se (2 uređaja)
        builder.setNegativeButton("Pridruži se (Igrač 2)", (d, w) -> {
            sessionId = input.getText().toString().trim().toUpperCase();
            isHost = false;
            myRole = "p2";
            soloMode = false;
            joinSession();
        });

        // Solo test – jedan uređaj, oba igrača naizmjenično
        builder.setNeutralButton("Solo test (1 uređaj)", (d, w) -> {
            soloMode = true;
            isHost = true;
            myRole = "p1";
            startSoloGame();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // ── Realtime: Host kreira sesiju ───────────────────────────────────────
    private void createSession() {
        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("kzz");

        Map<String, Object> data = new HashMap<>();
        data.put("questionIndex", 0);
        data.put("phase", "waiting_p2");
        data.put("p1Score", 0);
        data.put("p2Score", 0);
        data.put("p1Uid", myUid);
        data.put("p2Uid", "");
        data.put("p1Answer", -1);
        data.put("p2Answer", -1);
        data.put("questionFinished", false);

        sessionRef.set(data)
                .addOnSuccessListener(v -> {
                    showWaiting("Čekam Igrača 2...\nKod sesije: " + sessionId);
                    startListening();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Greška kreiranja sesije: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    // ── Realtime: Gost se pridružuje ───────────────────────────────────────
    private void joinSession() {
        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("kzz");

        sessionRef.update("p2Uid", myUid, "phase", "p1_turn")
                .addOnSuccessListener(v -> {
                    showWaiting("Pridružen! Čekam start...");
                    startListening();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Sesija nije pronađena. Provjeri kod!",
                                Toast.LENGTH_LONG).show());
    }

    // ── Realtime listener ──────────────────────────────────────────────────
    private void startListening() {
        sessionListener = sessionRef.addSnapshotListener((snapshot, error) -> {
            if (!isAdded() || snapshot == null || !snapshot.exists()) return;

            String newPhase = snapshot.getString("phase");
            long qIndex = getLong(snapshot, "questionIndex");
            player1Score = (int) getLong(snapshot, "p1Score");
            player2Score = (int) getLong(snapshot, "p2Score");

            if ("waiting_p2".equals(newPhase)) {
                showWaiting("Čekam Igrača 2...");
                return;
            }

            hideWaiting();

            if ("p1_turn".equals(newPhase) || "p2_turn".equals(newPhase)) {
                if ((int) qIndex != currentQuestion || !newPhase.equals(phase)) {
                    currentQuestion = (int) qIndex;
                    phase = newPhase;
                    renderQuestion(newPhase);
                } else {
                    updateScoreViews();
                }
            } else if ("finished".equals(newPhase)) {
                showEndGame();
            }
        });
    }

    // ── Solo mod ───────────────────────────────────────────────────────────
    private void startSoloGame() {
        currentQuestion = 0;
        player1Score = 0;
        player2Score = 0;
        phase = "p1_turn";
        hideWaiting();
        renderQuestion(phase);
    }

    // ── Prikaži pitanje ────────────────────────────────────────────────────
    private void renderQuestion(String currentPhase) {
        if (timer != null) timer.cancel();
        questionFinished = false;

        myTurn = soloMode
                || (isHost && "p1_turn".equals(currentPhase))
                || (!isHost && "p2_turn".equals(currentPhase));

        if (currentQuestion >= questions.size()) {
            if (soloMode) showEndGame();
            else endGameInFirestore();
            return;
        }

        KoZnaZnaRepository.Question q = questions.get(currentQuestion);
        tvQuestion.setText(q.question);
        for (int i = 0; i < 4; i++) {
            answerButtons[i].setText(q.answers.get(i));
            answerButtons[i].setEnabled(myTurn);
            answerButtons[i].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
            answerButtons[i].setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.dark_blue));
        }

        tvPlayer1Status.setText("p1_turn".equals(currentPhase) ? "▶ Igrač 1" : "Igrač 1");
        tvPlayer2Status.setText("p2_turn".equals(currentPhase) ? "▶ Igrač 2" : "Igrač 2");
        tvQuestionNum.setText("Pitanje " + (currentQuestion + 1) + "/" + questions.size());

        if (soloMode) {
            String ko = "p1_turn".equals(currentPhase) ? "Igrač 1" : "Igrač 2";
            tvInfo.setText("🎯 " + ko + " bira odgovor!");
        } else {
            tvInfo.setText(myTurn ? "🎯 Tvoj red!" : "⏳ Čekam protivnika...");
        }

        updateScoreViews();
        startTimer();
    }

    // ── Klik na odgovor ────────────────────────────────────────────────────
    private void onAnswerClick(int answerIndex) {
        if (questionFinished) return;
        if (!soloMode && !myTurn) return;

        for (Button btn : answerButtons) btn.setEnabled(false);
        questionFinished = true;
        if (timer != null) timer.cancel();

        KoZnaZnaRepository.Question q = questions.get(currentQuestion);
        boolean correct = (answerIndex == q.correctIndex);

        // Statistika – bilježi samo za ulogovanog igrača (P1 u solo ili pravi igrač u realtime)
        if (isHost && "p1_turn".equals(phase)) {
            if (correct) myCorrectCount++; else myWrongCount++;
        }

        if (soloMode) {
            handleSoloAnswer(answerIndex, correct, q);
        } else {
            handleRealtimeAnswer(answerIndex, correct, q);
        }
    }

    private void handleRealtimeAnswer(int answerIndex, boolean correct,
                                      KoZnaZnaRepository.Question q) {
        Map<String, Object> update = new HashMap<>();
        if (correct) {
            int nextQ = currentQuestion + 1;
            String nextPhase = nextQ < questions.size() ? "p1_turn" : "finished";
            if (isHost) update.put("p1Score", player1Score + 10);
            else        update.put("p2Score", player2Score + 10);
            update.put("questionIndex", nextQ);
            update.put("phase", nextPhase);
            markCorrectAnswer(q.correctIndex);
        } else {
            answerButtons[answerIndex].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light));
            if (isHost) {
                update.put("p1Score", player1Score - 5);
                update.put("phase", "p2_turn");
            } else {
                update.put("p2Score", player2Score - 5);
                int nextQ = currentQuestion + 1;
                String nextPhase = nextQ < questions.size() ? "p1_turn" : "finished";
                update.put("questionIndex", nextQ);
                update.put("phase", nextPhase);
            }
        }
        sessionRef.update(update);
    }

    private void handleSoloAnswer(int answerIndex, boolean correct,
                                  KoZnaZnaRepository.Question q) {
        if ("p1_turn".equals(phase)) {
            if (correct) {
                player1Score += 10;
                tvPlayer1Status.setText("✅ tačno +10");
                tvPlayer1Status.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                markCorrectAnswer(q.correctIndex);
                updateScoreViews();
                nextQuestionDelayed();
            } else {
                player1Score -= 5;
                tvPlayer1Status.setText("❌ netačno -5");
                tvPlayer1Status.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                answerButtons[answerIndex].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light));
                // Daj šansu Igraču 2 – reaktiviraj preostale dugmiće
                phase = "p2_turn";
                questionFinished = false;
                for (int i = 0; i < 4; i++) {
                    if (i != answerIndex) answerButtons[i].setEnabled(true);
                }
                tvInfo.setText("🎯 Igrač 2 bira odgovor!");
                updateScoreViews();
            }
        } else { // p2_turn
            if (correct) {
                player2Score += 10;
                tvPlayer2Status.setText("✅ tačno +10");
                tvPlayer2Status.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
            } else {
                player2Score -= 5;
                tvPlayer2Status.setText("❌ netačno -5");
                tvPlayer2Status.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                answerButtons[answerIndex].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light));
            }
            markCorrectAnswer(q.correctIndex);
            updateScoreViews();
            nextQuestionDelayed();
        }
    }

    private void nextQuestionDelayed() {
        questionFinished = true;
        new CountDownTimer(1500, 1500) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() {
                if (!isAdded()) return;
                currentQuestion++;
                phase = "p1_turn";
                if (currentQuestion < questions.size()) {
                    renderQuestion(phase);
                } else {
                    showEndGame();
                }
            }
        }.start();
    }

    // ── Timer ──────────────────────────────────────────────────────────────
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
                if (!isAdded() || questionFinished) return;
                tvTimer.setText("0");
                tvInfo.setText("Vreme isteklo!");
                questionFinished = true;
                for (Button btn : answerButtons) btn.setEnabled(false);

                if (soloMode) {
                    // U solo modu prelaz na sljedeće pitanje
                    nextQuestionDelayed();
                } else if (myTurn) {
                    Map<String, Object> update = new HashMap<>();
                    if (isHost) {
                        update.put("phase", "p2_turn");
                    } else {
                        int nextQ = currentQuestion + 1;
                        String nextPhase = nextQ < questions.size() ? "p1_turn" : "finished";
                        update.put("questionIndex", nextQ);
                        update.put("phase", nextPhase);
                    }
                    sessionRef.update(update);
                }
            }
        }.start();
    }

    // ── Kraj igre ──────────────────────────────────────────────────────────
    private void endGameInFirestore() {
        if (isHost && sessionRef != null) {
            sessionRef.update("phase", "finished");
        }
    }

    private void showEndGame() {
        if (timer != null) timer.cancel();
        if (!isAdded()) return;

        // Sačuvaj statistiku
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

    // ── Helpers ────────────────────────────────────────────────────────────
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

    private long getLong(com.google.firebase.firestore.DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        return 0L;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        if (sessionListener != null) sessionListener.remove();
    }
}