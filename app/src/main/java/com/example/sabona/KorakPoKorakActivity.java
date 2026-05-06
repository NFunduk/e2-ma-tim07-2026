package com.example.sabona;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class KorakPoKorakActivity extends AppCompatActivity {
    private TextView tvKorakRound, tvKorakPlayer, tvKorakTimer, tvKorakScore;
    private TextView tvKorakInfo, tvCurrentPoints;
    private TextView tvStep1, tvStep2, tvStep3, tvStep4, tvStep5, tvStep6, tvStep7;
    private EditText etKorakAnswer;
    private Button btnKorakGuess, btnKorakNext;

    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int currentStep = 0;  // koliko koraka je otvoreno
    private boolean roundFinished = false;

    private CountDownTimer timer;

    // Primer podataka
    private final String[][] steps = {
            // Runda 1
            {
                    "Osnovan 1687. godine",
                    "Nalazi se u Engleskoj",
                    "Jedna od najstarijih na svetu",
                    "Poznata po Njutnovom stablu jabuke",
                    "Smestena u Kembridžu",
                    "Ivy League",
                    "Čuveni univerzitet u UK"
            },
            // Runda 2
            {
                    "Prva ga je koristila u 2. veku",
                    "Napravljen od zlata i srebra",
                    "Nosila ga rimska carica",
                    "Sadrži dragoceno kamenje",
                    "Vrednija je od krune",
                    "Stavlja se na glavu",
                    "Simbol royaltyja"
            }
    };

    private final String[] answers = {"Kembridž", "Tijara"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        initViews();
        setupBottomNavigation();
        startRound();
    }

    private void initViews() {
        tvKorakRound = findViewById(R.id.tvKorakRound);
        tvKorakPlayer = findViewById(R.id.tvKorakPlayer);
        tvKorakTimer = findViewById(R.id.tvKorakTimer);
        tvKorakScore = findViewById(R.id.tvKorakScore);
        tvKorakInfo = findViewById(R.id.tvKorakInfo);
        tvCurrentPoints = findViewById(R.id.tvCurrentPoints);
        tvStep1 = findViewById(R.id.tvStep1);
        tvStep2 = findViewById(R.id.tvStep2);
        tvStep3 = findViewById(R.id.tvStep3);
        tvStep4 = findViewById(R.id.tvStep4);
        tvStep5 = findViewById(R.id.tvStep5);
        tvStep6 = findViewById(R.id.tvStep6);
        tvStep7 = findViewById(R.id.tvStep7);
        etKorakAnswer = findViewById(R.id.etKorakAnswer);
        btnKorakGuess = findViewById(R.id.btnKorakGuess);
        btnKorakNext = findViewById(R.id.btnKorakNext);

        btnKorakGuess.setOnClickListener(v -> checkAnswer());
        btnKorakNext.setOnClickListener(v -> {
            if (round == 1) {
                round = 2;
                currentPlayer = 2;
                startRound();
            } else {
                showEndGame();
            }
        });
    }

    private void startRound() {
        if (timer != null) timer.cancel();

        currentStep = 0;
        roundFinished = false;
        btnKorakNext.setVisibility(View.GONE);

        resetStepFields();
        etKorakAnswer.setText("");

        tvKorakInfo.setText("Čekaj — otvara se novi korak svakih 10 sekundi.");
        updateHeader();
        updatePoints();

        // Otvori prvi korak odmah
        openNextStep();

        // Tajmer koji otvara korak svakih 10 sekundi
        startStepTimer();
    }

    private void resetStepFields() {
        tvStep1.setText("???");
        tvStep2.setText("???");
        tvStep3.setText("???");
        tvStep4.setText("???");
        tvStep5.setText("???");
        tvStep6.setText("???");
        tvStep7.setText("???");
    }

    private void openNextStep() {
        if (currentStep >= 7) return;

        String stepText = steps[round - 1][currentStep];
        switch (currentStep) {
            case 0: tvStep1.setText(stepText); break;
            case 1: tvStep2.setText(stepText); break;
            case 2: tvStep3.setText(stepText); break;
            case 3: tvStep4.setText(stepText); break;
            case 4: tvStep5.setText(stepText); break;
            case 5: tvStep6.setText(stepText); break;
            case 6: tvStep7.setText(stepText); break;
        }
        currentStep++;
        updatePoints();
    }

    private void startStepTimer() {
        // Otvara korak svakih 10 sekundi
        timer = new CountDownTimer(70000, 1000) {
            private long stepCountdown = 10;

            @Override
            public void onTick(long millisUntilFinished) {
                long secondsLeft = millisUntilFinished / 1000;
                tvKorakTimer.setText(String.format("00:%02d", secondsLeft % 70));

                long elapsed = 70 - secondsLeft;
                if (elapsed > 0 && elapsed % 10 == 0 && currentStep < 7) {
                    openNextStep();
                }
            }

            @Override
            public void onFinish() {
                tvKorakTimer.setText("00:00");
                if (!roundFinished) {
                    tvKorakInfo.setText("Vreme je isteklo! Rešenje: " + answers[round - 1]);
                    finishRound();
                }
            }
        };
        timer.start();
    }

    private void checkAnswer() {
        if (roundFinished) return;

        String guess = etKorakAnswer.getText().toString().trim();
        if (guess.isEmpty()) {
            Toast.makeText(this, "Unesi odgovor", Toast.LENGTH_SHORT).show();
            return;
        }

        if (guess.equalsIgnoreCase(answers[round - 1])) {
            // Tačan odgovor: 20 - (currentStep-1)*2 bodova
            int points = Math.max(20 - (currentStep - 1) * 2, 0);
            addPoints(points);
            tvKorakInfo.setText("Tačno! Osvajate " + points + " bodova.");
            finishRound();
        } else {
            Toast.makeText(this, "Netačno, pokušaj ponovo.", Toast.LENGTH_SHORT).show();
        }

        updateHeader();
    }

    private void updatePoints() {
        int points = Math.max(20 - (currentStep) * 2, 0);
        tvCurrentPoints.setText(String.valueOf(points));
    }

    private void addPoints(int points) {
        if (currentPlayer == 1) player1Score += points;
        else player2Score += points;
    }

    private void finishRound() {
        if (timer != null) timer.cancel();
        roundFinished = true;
        btnKorakNext.setVisibility(View.VISIBLE);
        btnKorakNext.setText(round == 1 ? "Sledeća runda" : "Završi igru");
        updateHeader();
    }

    private void updateHeader() {
        tvKorakRound.setText("Runda " + round + "/2");
        tvKorakPlayer.setText("Na potezu: igrač " + currentPlayer);
        tvKorakScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private void showEndGame() {
        String winner;
        if (player1Score > player2Score) winner = "Pobedio igrač 1!";
        else if (player2Score > player1Score) winner = "Pobedio igrač 2!";
        else winner = "Nerešeno!";

        Toast.makeText(this, winner + " Sledi Moj broj.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(KorakPoKorakActivity.this, MojBrojActivity.class));
        finish();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.play);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return id == R.id.play;
        });
    }
}
