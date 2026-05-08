package com.example.sabona;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class KoZnaZnaActivity extends AppCompatActivity {

    // Views
    private TextView tvQuestionNum, tvTimer, tvScore, tvInfo, tvQuestion;
    private TextView tvPlayer1Status, tvPlayer2Status, tvScore1, tvScore2;
    private Button btnAnswer1, btnAnswer2, btnAnswer3, btnAnswer4, btnNext;
    private Button[] answerButtons = new Button[4];

    // Timer
    private CountDownTimer timer;

    // ---- Stanje igre ----
    private int currentQuestion = 0;
    private int player1Score = 0;
    private int player2Score = 0;

    // Ko je u ovom pitanju vec odgovorio (simulacija 2 igraca na jednom uredjaju)
    // U realnoj igri ovo bi bilo sinhronizovano
    private boolean player1Answered = false;
    private boolean player2Answered = false;

    // Trenutno aktivan igrac koji treba da odgovori (1 ili 2)
    // Na jednom uredjaju naizmjenicno igraju
    private int activePlayer = 1;

    // Da li je pitanje zavrseno
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ko_zna_zna);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.koZnaZnaRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        connectViews();
        setupClicks();
        setupBottomNavigation();
        loadQuestion();
    }

    private void connectViews() {
        tvQuestionNum   = findViewById(R.id.tvQuestionNum);
        tvTimer         = findViewById(R.id.tvTimer);
        tvScore         = findViewById(R.id.tvScore);
        tvInfo          = findViewById(R.id.tvInfo);
        tvQuestion      = findViewById(R.id.tvQuestion);
        tvPlayer1Status = findViewById(R.id.tvPlayer1Status);
        tvPlayer2Status = findViewById(R.id.tvPlayer2Status);
        tvScore1        = findViewById(R.id.tvScore1);
        tvScore2        = findViewById(R.id.tvScore2);
        //btnNext         = findViewById(R.id.btnNext);

        answerButtons[0] = findViewById(R.id.btnAnswer1);
        answerButtons[1] = findViewById(R.id.btnAnswer2);
        answerButtons[2] = findViewById(R.id.btnAnswer3);
        answerButtons[3] = findViewById(R.id.btnAnswer4);
    }

    private void setupClicks() {
        for (int i = 0; i < 4; i++) {
            int answerIndex = i;
            answerButtons[i].setOnClickListener(v -> onAnswerClick(answerIndex));
        }

        /*btnNext.setOnClickListener(v -> {
            currentQuestion++;
            if (currentQuestion < questions.length) {
                loadQuestion();
            } else {
                showEndGame();
            }
        });*/
    }

    private void loadQuestion() {
        if (timer != null) timer.cancel();

        // Reset stanja za ovo pitanje
        player1Answered = false;
        player2Answered = false;
        questionFinished = false;
        activePlayer = 1;
        //btnNext.setVisibility(View.GONE);

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
            btn.setBackgroundTintList(getColorStateList(R.color.white));
            btn.setTextColor(getColor(R.color.dark_blue));
        }

        // Reset statusa igraca
        tvPlayer1Status.setText("⏳ čeka");
        tvPlayer2Status.setText("⏳ čeka");
        tvPlayer1Status.setTextColor(getColor(R.color.dark_blue));
        tvPlayer2Status.setTextColor(getColor(R.color.dark_blue));

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
                // Igrac 1 tacno - dobija bodove, pitanje zavrseno
                player1Score += 10;
                tvPlayer1Status.setText("✅ tačno!");
                tvPlayer1Status.setTextColor(getColor(android.R.color.holo_green_dark));
                tvInfo.setText("Igrač 1 tačno odgovorio! +10 bodova");
                markCorrectAnswer(correctIndex);
                finishQuestion();
            } else {
                // Igrac 1 netacno - gubi bodove, igrac 2 ima sansu
                player1Score -= 5;
                if (player1Score < 0) player1Score = 0;
                tvPlayer1Status.setText("❌ netačno");
                tvPlayer1Status.setTextColor(getColor(android.R.color.holo_red_dark));
                answerButtons[answerIndex].setBackgroundTintList(
                        getColorStateList(android.R.color.holo_red_light));

                // Prebaci na igraca 2
                activePlayer = 2;
                tvInfo.setText("Igrač 1 netačno! Na potezu: Igrač 2");
                updateScoreViews();
            }

        } else {
            // Igrac 2 odgovara
            player2Answered = true;

            if (isCorrect) {
                player2Score += 10;
                tvPlayer2Status.setText("✅ tačno!");
                tvPlayer2Status.setTextColor(getColor(android.R.color.holo_green_dark));
                tvInfo.setText("Igrač 2 tačno odgovorio! +10 bodova");
                markCorrectAnswer(correctIndex);
                finishQuestion();
            } else {
                player2Score -= 5;
                if (player2Score < 0) player2Score = 0;
                tvPlayer2Status.setText("❌ netačno");
                tvPlayer2Status.setTextColor(getColor(android.R.color.holo_red_dark));
                answerButtons[answerIndex].setBackgroundTintList(
                        getColorStateList(android.R.color.holo_red_light));
                tvInfo.setText("Oba igrača su odgovorila netačno.");
                markCorrectAnswer(correctIndex);
                finishQuestion();
            }
        }

        updateScoreViews();
    }

    private void markCorrectAnswer(int correctIndex) {
        answerButtons[correctIndex].setBackgroundTintList(
                getColorStateList(android.R.color.holo_green_dark));
        answerButtons[correctIndex].setTextColor(getColor(R.color.white));
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
            public void onTick(long millisUntilFinished) {
                // ne treba nista
            }

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
        tvTimer.setTextColor(getColor(R.color.petal));

        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvTimer.setText(String.valueOf(seconds));

                // Postani crven kad ostane 2 sekunde
                if (seconds <= 2) {
                    tvTimer.setTextColor(getColor(android.R.color.holo_red_light));
                }
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0");
                tvInfo.setText("Vreme je isteklo!");

                if (!questionFinished) {
                    int correctIndex = (int) questions[currentQuestion][5];
                    markCorrectAnswer(correctIndex);
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

        Toast.makeText(this, winner, Toast.LENGTH_LONG).show();

        // Sledeca igra je Spojnice
        Intent intent = new Intent(KoZnaZnaActivity.this, SpojniceActivity.class);
        startActivity(intent);
        finish();
    }

    private void updateScoreViews() {
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
        tvScore1.setText(player1Score + " bod.");
        tvScore2.setText(player2Score + " bod.");
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.play);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.home) {
                startActivity(new Intent(KoZnaZnaActivity.this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.play) {
                return true;
            } else if (id == R.id.profile) {
                startActivity(new Intent(KoZnaZnaActivity.this, ProfileActivity.class));
                return true;
            } else if (id == R.id.rank) {
                return true;
            } else if (id == R.id.friends) {
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}