package com.example.sabona;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class KoZnaZnaFragment extends Fragment {

    // Views
    private TextView tvQuestionNum, tvTimer, tvScore, tvInfo, tvQuestion;
    private TextView tvPlayer1Status, tvPlayer2Status, tvScore1, tvScore2;
    private Button[] answerButtons = new Button[4];
    private Button btnNext;

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

    // Mokap pitanja: {pitanje, odg1, odg2, odg3, odg4, indexTacnog(0-3)}
    private final Object[][] questions = {
            {"Koji je glavni grad Francuske?",
                    "A) London", "B) Berlin", "C) Pariz", "D) Madrid", 2},
            {"Ko je napisao 'Romeo i Julija'?",
                    "A) Dickens", "B) Shakespeare", "C) Tolstoj", "D) Hugo", 1},
            {"Koliko planeta ima Sunčev sistem?",
                    "A) 7", "B) 9", "C) 8", "D) 10", 2},
            {"Koja je najveća država na svijetu?",
                    "A) Kanada", "B) SAD", "C) Kina", "D) Rusija", 3},
            {"Koji element ima hemijski simbol 'O'?",
                    "A) Zlato", "B) Kiseonik", "C) Osmijum", "D) Olovo", 1}
    };

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

        answerButtons[0] = view.findViewById(R.id.btnAnswer1);
        answerButtons[1] = view.findViewById(R.id.btnAnswer2);
        answerButtons[2] = view.findViewById(R.id.btnAnswer3);
        answerButtons[3] = view.findViewById(R.id.btnAnswer4);

        setupClicks();
        loadQuestion();
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
            if (currentQuestion < questions.length) {
                loadQuestion();
            } else {
                showEndGame();
            }
        });
    }

    private void loadQuestion() {
        if (timer != null) timer.cancel();

        // Reset stanja za ovo pitanje
        player1Answered = false;
        player2Answered = false;
        questionFinished = false;
        activePlayer = 1;
        btnNext.setVisibility(View.GONE);

        // Postavi tekst pitanja i odgovora
        Object[] q = questions[currentQuestion];
        tvQuestion.setText((String) q[0]);
        answerButtons[0].setText((String) q[1]);
        answerButtons[1].setText((String) q[2]);
        answerButtons[2].setText((String) q[3]);
        answerButtons[3].setText((String) q[4]);

        // Reset boja dugmadi
        for (Button btn : answerButtons) {
            btn.setEnabled(true);
            btn.setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
            btn.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.dark_blue));
        }

        // Reset statusa igraca
        tvPlayer1Status.setText("⏳ čeka");
        tvPlayer2Status.setText("⏳ čeka");
        tvPlayer1Status.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.dark_blue));
        tvPlayer2Status.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.dark_blue));

        tvQuestionNum.setText("Pitanje " + (currentQuestion + 1) + "/" + questions.length);
        tvInfo.setText("Na potezu: Igrač " + activePlayer + " - odaberi odgovor!");

        updateScoreViews();
        startTimer();
    }

    private void onAnswerClick(int answerIndex) {
        if (questionFinished) return;

        int correctIndex = (int) questions[currentQuestion][5];
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
                if (player1Score < 0) player1Score = 0;
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
                if (player2Score < 0) player2Score = 0;
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

        // Automatski prelaz na sljedece pitanje nakon 2 sekunde
        new CountDownTimer(2000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) { }

            @Override
            public void onFinish() {
                currentQuestion++;
                if (currentQuestion < questions.length) {
                    loadQuestion();
                } else {
                    showEndGame();
                }
            }
        }.start();

        updateScoreViews();
    }

    private void startTimer() {
        tvTimer.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.petal));

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
                tvTimer.setText("0");
                tvInfo.setText("Vreme je isteklo!");

                if (!questionFinished) {
                    int correctIndex = (int) questions[currentQuestion][5];
                    markCorrectAnswerTimeout(correctIndex); // <- ovo umjesto markCorrectAnswer
                    finishQuestion();
                }
            }
        };

        timer.start();
    }

    private void showEndGame() {
        String winner;
        if (player1Score > player2Score) {
            winner = "Pobjednik Ko zna zna: Igrač 1!";
        } else if (player2Score > player1Score) {
            winner = "Pobjednik Ko zna zna: Igrač 2!";
        } else {
            winner = "Ko zna zna je nerješeno!";
        }

        Toast.makeText(requireContext(), winner, Toast.LENGTH_LONG).show();

        // Navigacija na sljedecu igru (zamijeni action_kozna_to_sljedeci sa tvojim action ID-om)
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_kozna_to_spojnice);
    }

    private void updateScoreViews() {
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
        tvScore1.setText(player1Score + " bod.");
        tvScore2.setText(player2Score + " bod.");
    }
}