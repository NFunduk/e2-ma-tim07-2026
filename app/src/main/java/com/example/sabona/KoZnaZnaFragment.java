package com.example.sabona;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.example.sabona.repository.KoZnaZnaRepository;

import java.util.Collections;
import java.util.List;

public class KoZnaZnaFragment extends Fragment {

    // Views
    private TextView tvQuestionNum, tvTimer, tvScore, tvInfo, tvQuestion;
    private TextView tvPlayer1Status, tvPlayer2Status, tvScore1, tvScore2;
    private Button[] answerButtons = new Button[4];
    private Button btnNext;
    private ProgressBar progressBar;
    private View layoutGame;

    // Timer
    private CountDownTimer timer;

    // ---- Stanje igre ----
    private int currentQuestion = 0;
    private int player1Score = 0;
    private int player2Score = 0;
    private boolean player1Answered = false;
    private boolean player2Answered = false;
    private int activePlayer = 1;
    private boolean questionFinished = false;

    // Pitanja iz Firestorea
    private List<KoZnaZnaRepository.Question> questions;
    private final KoZnaZnaRepository repository = new KoZnaZnaRepository();

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
        btnNext         = view.findViewById(R.id.btnNext);
        progressBar     = view.findViewById(R.id.progressBar);
        layoutGame      = view.findViewById(R.id.layoutGame);

        // Sakrij igru dok se pitanja učitavaju
        layoutGame.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        answerButtons[0] = view.findViewById(R.id.btnAnswer1);
        answerButtons[1] = view.findViewById(R.id.btnAnswer2);
        answerButtons[2] = view.findViewById(R.id.btnAnswer3);
        answerButtons[3] = view.findViewById(R.id.btnAnswer4);

        loadQuestionsFromFirestore();
    }

    private void loadQuestionsFromFirestore() {
        repository.fetchQuestions(new KoZnaZnaRepository.QuestionsCallback() {
            @Override
            public void onSuccess(List<KoZnaZnaRepository.Question> loaded) {
                if (!isAdded()) return;

                Collections.shuffle(loaded); // Miješaj redoslijed pitanja
                // Uzmi max 5 pitanja
                questions = loaded.size() > 5 ? loaded.subList(0, 5) : loaded;

                progressBar.setVisibility(View.GONE);
                layoutGame.setVisibility(View.VISIBLE);

                setupClicks();
                loadQuestion();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
    }

    private void setupClicks() {
        for (int i = 0; i < 4; i++) {
            int answerIndex = i;
            answerButtons[i].setOnClickListener(v -> onAnswerClick(answerIndex));
        }

        btnNext.setOnClickListener(v -> {
            currentQuestion++;
            if (currentQuestion < questions.size()) {
                loadQuestion();
            } else {
                showEndGame();
            }
        });
    }

    private void loadQuestion() {
        if (timer != null) timer.cancel();

        player1Answered = false;
        player2Answered = false;
        questionFinished = false;
        activePlayer = 1;
        btnNext.setVisibility(View.GONE);

        KoZnaZnaRepository.Question q = questions.get(currentQuestion);
        tvQuestion.setText(q.question);
        for (int i = 0; i < 4; i++) {
            answerButtons[i].setText(q.answers.get(i));
        }

        for (Button btn : answerButtons) {
            btn.setEnabled(true);
            btn.setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
            btn.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.dark_blue));
        }

        tvPlayer1Status.setText("⏳ čeka");
        tvPlayer2Status.setText("⏳ čeka");
        tvPlayer1Status.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.dark_blue));
        tvPlayer2Status.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.dark_blue));

        tvQuestionNum.setText("Pitanje " + (currentQuestion + 1) + "/" + questions.size());
        tvInfo.setText("Na potezu: Igrač " + activePlayer + " - odaberi odgovor!");

        updateScoreViews();
        startTimer();
    }

    private void onAnswerClick(int answerIndex) {
        if (questionFinished) return;

        int correctIndex = questions.get(currentQuestion).correctIndex;
        boolean isCorrect = (answerIndex == correctIndex);

        if (activePlayer == 1) {
            player1Answered = true;
            if (isCorrect) {
                player1Score += 10;
                tvPlayer1Status.setText("✅ tačno!");
                tvPlayer1Status.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                tvInfo.setText("Igrač 1 tačno odgovorio! +10 bodova");
                markCorrectAnswer(correctIndex);
                finishQuestion();
            } else {
                player1Score -= 5;
                tvPlayer1Status.setText("❌ netačno");
                tvPlayer1Status.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                answerButtons[answerIndex].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light));
                activePlayer = 2;
                tvInfo.setText("Igrač 1 netačno! Na potezu: Igrač 2");
                updateScoreViews();
            }
        } else {
            player2Answered = true;
            if (isCorrect) {
                player2Score += 10;
                tvPlayer2Status.setText("✅ tačno!");
                tvPlayer2Status.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
                tvInfo.setText("Igrač 2 tačno odgovorio! +10 bodova");
                markCorrectAnswer(correctIndex);
                finishQuestion();
            } else {
                player2Score -= 5;
                tvPlayer2Status.setText("❌ netačno");
                tvPlayer2Status.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
                answerButtons[answerIndex].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light));
                tvInfo.setText("Oba igrača su odgovorila netačno.");
                markCorrectAnswer(correctIndex);
                finishQuestion();
            }
        }

        updateScoreViews();
    }

    private void markCorrectAnswer(int correctIndex) {
        answerButtons[correctIndex].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
        answerButtons[correctIndex].setTextColor(
                ContextCompat.getColor(requireContext(), R.color.white));
    }

    private void markCorrectAnswerTimeout(int correctIndex) {
        answerButtons[correctIndex].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), R.color.lavender));
        answerButtons[correctIndex].setTextColor(
                ContextCompat.getColor(requireContext(), R.color.dark_blue));
    }

    private void finishQuestion() {
        if (timer != null) timer.cancel();
        questionFinished = true;

        for (Button btn : answerButtons) {
            btn.setEnabled(false);
        }

        new CountDownTimer(2000, 1000) {
            @Override public void onTick(long millisUntilFinished) {}
            @Override
            public void onFinish() {
                if (!isAdded()) return;
                currentQuestion++;
                if (currentQuestion < questions.size()) {
                    loadQuestion();
                } else {
                    showEndGame();
                }
            }
        }.start();

        updateScoreViews();
    }

    private void startTimer() {
        tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.petal));

        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvTimer.setText(String.valueOf(seconds));
                if (seconds <= 2) {
                    tvTimer.setTextColor(
                            ContextCompat.getColor(requireContext(), android.R.color.holo_red_light));
                }
            }

            @Override
            public void onFinish() {
                if (!isAdded()) return;
                tvTimer.setText("0");
                tvInfo.setText("Vreme je isteklo!");
                if (!questionFinished) {
                    int correctIndex = questions.get(currentQuestion).correctIndex;
                    markCorrectAnswerTimeout(correctIndex);
                    finishQuestion();
                }
            }
        };
        timer.start();
    }

    private void showEndGame() {
        // Sačuvaj rezultate u Firestore
        saveResults();

        String winner;
        if (player1Score > player2Score) {
            winner = "Pobjednik Ko zna zna: Igrač 1! (" + player1Score + " : " + player2Score + ")";
        } else if (player2Score > player1Score) {
            winner = "Pobjednik Ko zna zna: Igrač 2! (" + player1Score + " : " + player2Score + ")";
        } else {
            winner = "Ko zna zna je nerješeno! (" + player1Score + " : " + player2Score + ")";
        }

        Toast.makeText(requireContext(), winner, Toast.LENGTH_LONG).show();

        NavHostFragment.findNavController(this)
                .navigate(R.id.action_kozna_to_spojnice);
    }

    private void saveResults() {
        // TODO: Zamijeniti "mockPlayer1" i "mockPlayer2" sa pravim
        // FirebaseAuth.getInstance().getCurrentUser().getUid() kad student 1 završi Auth
        String player1Id = "mockPlayer1";
        String player2Id = "mockPlayer2";

        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("player1Score", player1Score);
        result.put("player2Score", player2Score);
        result.put("player1Id", player1Id);
        result.put("player2Id", player2Id);
        result.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("game_results_ko_zna_zna")
                .add(result)
                .addOnSuccessListener(ref -> {
                    // Rezultat sačuvan
                })
                .addOnFailureListener(e -> {
                    // Tiha greška, ne prekidamo tok igre
                });
    }

    private void updateScoreViews() {
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
        tvScore1.setText(player1Score + " bod.");
        tvScore2.setText(player2Score + " bod.");
    }
}