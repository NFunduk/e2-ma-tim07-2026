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

import java.util.Random;

public class MojBrojActivity extends AppCompatActivity {
    private TextView tvMojBrojRound, tvMojBrojPlayer, tvMojBrojTimer, tvMojBrojScore;
    private TextView tvMojBrojInfo, tvTargetNumber;
    private TextView tvNum1, tvNum2, tvNum3, tvNum4, tvNum5, tvNum6;
    private TextView tvResult;
    private Button btnStopTarget, btnStopNumbers;
    private Button btnOpPlus, btnOpMinus, btnOpMultiply, btnOpDivide;
    private Button btnOpOpenParen, btnOpCloseParen;
    private Button btnClearExpression, btnSubmitExpression, btnMojBrojNext;
    private EditText etExpression;

    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    private int targetNumber = 0;
    private int[] availableNumbers = new int[6];
    private boolean targetRevealed = false;
    private boolean numbersRevealed = false;
    private boolean roundFinished = false;

    private CountDownTimer timer;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        initViews();
        setupOperandButtons();
        setupBottomNavigation();
        startRound();
    }

    private void initViews() {
        tvMojBrojRound = findViewById(R.id.tvMojBrojRound);
        tvMojBrojPlayer = findViewById(R.id.tvMojBrojPlayer);
        tvMojBrojTimer = findViewById(R.id.tvMojBrojTimer);
        tvMojBrojScore = findViewById(R.id.tvMojBrojScore);
        tvMojBrojInfo = findViewById(R.id.tvMojBrojInfo);
        tvTargetNumber = findViewById(R.id.tvTargetNumber);
        tvNum1 = findViewById(R.id.tvNum1);
        tvNum2 = findViewById(R.id.tvNum2);
        tvNum3 = findViewById(R.id.tvNum3);
        tvNum4 = findViewById(R.id.tvNum4);
        tvNum5 = findViewById(R.id.tvNum5);
        tvNum6 = findViewById(R.id.tvNum6);
        tvResult = findViewById(R.id.tvResult);
        btnStopTarget = findViewById(R.id.btnStopTarget);
        btnStopNumbers = findViewById(R.id.btnStopNumbers);
        btnOpPlus = findViewById(R.id.btnOpPlus);
        btnOpMinus = findViewById(R.id.btnOpMinus);
        btnOpMultiply = findViewById(R.id.btnOpMultiply);
        btnOpDivide = findViewById(R.id.btnOpDivide);
        btnOpOpenParen = findViewById(R.id.btnOpOpenParen);
        btnOpCloseParen = findViewById(R.id.btnOpCloseParen);
        btnClearExpression = findViewById(R.id.btnClearExpression);
        btnSubmitExpression = findViewById(R.id.btnSubmitExpression);
        btnMojBrojNext = findViewById(R.id.btnMojBrojNext);
        etExpression = findViewById(R.id.etExpression);

        btnStopTarget.setOnClickListener(v -> revealTarget());
        btnStopNumbers.setOnClickListener(v -> revealNumbers());
        btnClearExpression.setOnClickListener(v -> clearExpression());
        btnSubmitExpression.setOnClickListener(v -> submitExpression());
        btnMojBrojNext.setOnClickListener(v -> {
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

        targetRevealed = false;
        numbersRevealed = false;
        roundFinished = false;

        targetNumber = 0;
        tvTargetNumber.setText("???");
        tvNum1.setText("?"); tvNum2.setText("?"); tvNum3.setText("?");
        tvNum4.setText("?"); tvNum5.setText("?"); tvNum6.setText("?");
        etExpression.setText("");
        tvResult.setText("—");
        btnMojBrojNext.setVisibility(View.GONE);
        btnStopTarget.setEnabled(true);
        btnStopNumbers.setEnabled(true);

        tvMojBrojInfo.setText("Pritisni STOP da vidiš traženi broj.");
        updateHeader();

        startRoundTimer();
    }

    private void revealTarget() {
        if (targetRevealed) return;
        targetRevealed = true;

        // Generisanje traženog broja (100-999)
        targetNumber = 100 + random.nextInt(900);
        tvTargetNumber.setText(String.valueOf(targetNumber));
        btnStopTarget.setEnabled(false);

        tvMojBrojInfo.setText("Traženi broj: " + targetNumber + ". Sada pritisni STOP za ponuđene brojeve.");
    }

    private void revealNumbers() {
        if (!targetRevealed) {
            revealTarget();
        }
        if (numbersRevealed) return;
        numbersRevealed = true;

        int[] small = {1 + random.nextInt(9), 1 + random.nextInt(9),
                1 + random.nextInt(9), 1 + random.nextInt(9)};
        int[] medium = {10, 15, 20};
        int[] large = {25, 50, 75, 100};

        availableNumbers[0] = small[0];
        availableNumbers[1] = small[1];
        availableNumbers[2] = small[2];
        availableNumbers[3] = small[3];
        availableNumbers[4] = medium[random.nextInt(3)];
        availableNumbers[5] = large[random.nextInt(4)];

        shuffleNumbers();

        tvNum1.setText(String.valueOf(availableNumbers[0]));
        tvNum2.setText(String.valueOf(availableNumbers[1]));
        tvNum3.setText(String.valueOf(availableNumbers[2]));
        tvNum4.setText(String.valueOf(availableNumbers[3]));
        tvNum5.setText(String.valueOf(availableNumbers[4]));
        tvNum6.setText(String.valueOf(availableNumbers[5]));

        btnStopNumbers.setEnabled(false);
        tvMojBrojInfo.setText("Koristi ponuđene brojeve da dobiješ " + targetNumber + ".");
    }

    private void shuffleNumbers() {
        for (int i = availableNumbers.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = availableNumbers[i];
            availableNumbers[i] = availableNumbers[j];
            availableNumbers[j] = temp;
        }
    }

    private void setupOperandButtons() {
        // Svaki operand dodaje se u polje za izraz
        btnOpPlus.setOnClickListener(v -> appendToExpression(" + "));
        btnOpMinus.setOnClickListener(v -> appendToExpression(" - "));
        btnOpMultiply.setOnClickListener(v -> appendToExpression(" * "));
        btnOpDivide.setOnClickListener(v -> appendToExpression(" / "));
        btnOpOpenParen.setOnClickListener(v -> appendToExpression("("));
        btnOpCloseParen.setOnClickListener(v -> appendToExpression(")"));

        // Klikom na broj u polju dodaje se u izraz
        tvNum1.setOnClickListener(v -> appendToExpression(tvNum1.getText().toString()));
        tvNum2.setOnClickListener(v -> appendToExpression(tvNum2.getText().toString()));
        tvNum3.setOnClickListener(v -> appendToExpression(tvNum3.getText().toString()));
        tvNum4.setOnClickListener(v -> appendToExpression(tvNum4.getText().toString()));
        tvNum5.setOnClickListener(v -> appendToExpression(tvNum5.getText().toString()));
        tvNum6.setOnClickListener(v -> appendToExpression(tvNum6.getText().toString()));
    }

    private void appendToExpression(String value) {
        if (roundFinished || !numbersRevealed) return;
        String current = etExpression.getText().toString();
        etExpression.setText(current + value);
    }

    private void clearExpression() {
        etExpression.setText("");
        tvResult.setText("—");
    }

    private void submitExpression() {
        if (roundFinished) return;

        String expr = etExpression.getText().toString().trim();
        if (expr.isEmpty()) {
            Toast.makeText(this, "Unesi izraz", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Evaluacija izraza
        Toast.makeText(this, "Izraz primljen: " + expr, Toast.LENGTH_SHORT).show();
        tvResult.setText("?"); // TODO: izračunaj

        // Za sada simuliramo kraj runde
        finishRound();
    }

    private void startRoundTimer() {
        timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvMojBrojTimer.setText(String.format("0%d:%02d", seconds / 60, seconds % 60));

                // Auto-reveal traženog broja posle 5s
                if (seconds == 55 && !targetRevealed) {
                    revealTarget();
                }
                // Auto-reveal ponuđenih brojeva posle 10s
                if (seconds == 50 && !numbersRevealed) {
                    revealNumbers();
                }
            }

            @Override
            public void onFinish() {
                tvMojBrojTimer.setText("00:00");
                if (!roundFinished) {
                    tvMojBrojInfo.setText("Vreme je isteklo!");
                    finishRound();
                }
            }
        };
        timer.start();
    }

    private void addPoints(int points) {
        if (currentPlayer == 1) player1Score += points;
        else player2Score += points;
    }

    private void finishRound() {
        if (timer != null) timer.cancel();
        roundFinished = true;
        btnMojBrojNext.setVisibility(View.VISIBLE);
        btnMojBrojNext.setText(round == 1 ? "Sledeća runda" : "Završi igru");
        updateHeader();
    }

    private void updateHeader() {
        tvMojBrojRound.setText("Runda " + round + "/2");
        tvMojBrojPlayer.setText("Na potezu: igrač " + currentPlayer);
        tvMojBrojScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private void showEndGame() {
        String winner;
        if (player1Score > player2Score) winner = "Pobedio igrač 1!";
        else if (player2Score > player1Score) winner = "Pobedio igrač 2!";
        else winner = "Nerešeno!";

        Toast.makeText(this, winner + " Kraj partije.", Toast.LENGTH_LONG).show();
        // TODO: Navigacija ka sledećoj igri u sekvenci partije
        startActivity(new Intent(MojBrojActivity.this, MainActivity.class));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }


}
