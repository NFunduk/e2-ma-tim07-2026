package com.example.sabona;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AssociationsActivity extends AppCompatActivity {

    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo;
    private Button[][] fieldButtons = new Button[4][4];
    private TextView[] columnSolutions = new TextView[4];
    private EditText[] columnInputs = new EditText[4];
    private Button[] guessColumnButtons = new Button[4];
    private EditText finalInput;
    private Button btnGuessFinal, btnNextRound;

    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    private boolean[][] opened = new boolean[4][4];
    private boolean[] columnSolved = new boolean[4];
    private boolean finalSolved = false;

    private CountDownTimer timer;

    private final String[][][] fields = {
            {
                    {"Lav", "Tigar", "Vuk", "Medved"},
                    {"Jabuka", "Kruška", "Šljiva", "Breskva"},
                    {"Crvena", "Plava", "Žuta", "Zelena"},
                    {"Gitara", "Klavir", "Violina", "Bubanj"}
            },
            {
                    {"Sava", "Dunav", "Tisa", "Morava"},
                    {"Zima", "Leto", "Proleće", "Jesen"},
                    {"Fudbal", "Tenis", "Košarka", "Odbojka"},
                    {"Zlato", "Srebro", "Bronza", "Medalja"}
            }
    };

    private final String[][] columnAnswers = {
            {"Životinje", "Voće", "Boje", "Instrumenti"},
            {"Reke", "Godišnja doba", "Sportovi", "Nagrada"}
    };

    private final String[] finalAnswers = {
            "Pojmovi",
            "Takmičenje"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_associations);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.associationsRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        connectViews();
        setupClicks();
        startRound();
    }

    private void connectViews() {
        tvRound = findViewById(R.id.tvRound);
        tvPlayer = findViewById(R.id.tvPlayer);
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
        tvInfo = findViewById(R.id.tvInfo);

        fieldButtons[0][0] = findViewById(R.id.a1);
        fieldButtons[0][1] = findViewById(R.id.a2);
        fieldButtons[0][2] = findViewById(R.id.a3);
        fieldButtons[0][3] = findViewById(R.id.a4);

        fieldButtons[1][0] = findViewById(R.id.b1);
        fieldButtons[1][1] = findViewById(R.id.b2);
        fieldButtons[1][2] = findViewById(R.id.b3);
        fieldButtons[1][3] = findViewById(R.id.b4);

        fieldButtons[2][0] = findViewById(R.id.c1);
        fieldButtons[2][1] = findViewById(R.id.c2);
        fieldButtons[2][2] = findViewById(R.id.c3);
        fieldButtons[2][3] = findViewById(R.id.c4);

        fieldButtons[3][0] = findViewById(R.id.d1);
        fieldButtons[3][1] = findViewById(R.id.d2);
        fieldButtons[3][2] = findViewById(R.id.d3);
        fieldButtons[3][3] = findViewById(R.id.d4);

        columnSolutions[0] = findViewById(R.id.solutionA);
        columnSolutions[1] = findViewById(R.id.solutionB);
        columnSolutions[2] = findViewById(R.id.solutionC);
        columnSolutions[3] = findViewById(R.id.solutionD);

        columnInputs[0] = findViewById(R.id.inputA);
        columnInputs[1] = findViewById(R.id.inputB);
        columnInputs[2] = findViewById(R.id.inputC);
        columnInputs[3] = findViewById(R.id.inputD);

        guessColumnButtons[0] = findViewById(R.id.btnGuessA);
        guessColumnButtons[1] = findViewById(R.id.btnGuessB);
        guessColumnButtons[2] = findViewById(R.id.btnGuessC);
        guessColumnButtons[3] = findViewById(R.id.btnGuessD);

        finalInput = findViewById(R.id.inputFinal);
        btnGuessFinal = findViewById(R.id.btnGuessFinal);
        btnNextRound = findViewById(R.id.btnNextRound);
    }

    private void setupClicks() {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                int finalCol = col;
                int finalRow = row;
                fieldButtons[col][row].setOnClickListener(v -> openField(finalCol, finalRow));
            }
        }

        for (int col = 0; col < 4; col++) {
            int finalCol = col;
            guessColumnButtons[col].setOnClickListener(v -> guessColumn(finalCol));
        }

        btnGuessFinal.setOnClickListener(v -> guessFinal());

        btnNextRound.setOnClickListener(v -> {
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
        if (timer != null) {
            timer.cancel();
        }

        int boardIndex = round - 1;
        finalSolved = false;
        btnNextRound.setVisibility(View.GONE);

        for (int col = 0; col < 4; col++) {
            columnSolved[col] = false;
            columnSolutions[col].setText("?");
            columnInputs[col].setText("");
            guessColumnButtons[col].setEnabled(true);

            for (int row = 0; row < 4; row++) {
                opened[col][row] = false;
                fieldButtons[col][row].setText("?");
                fieldButtons[col][row].setEnabled(true);
            }
        }

        finalInput.setText("");
        btnGuessFinal.setEnabled(true);

        tvInfo.setText("Otvori polje ili pogodi kolonu / konačno rešenje.");
        updateHeader();
        startTimer();
    }

    private void openField(int col, int row) {
        if (opened[col][row] || columnSolved[col] || finalSolved) {
            return;
        }

        opened[col][row] = true;
        fieldButtons[col][row].setText(fields[round - 1][col][row]);
        fieldButtons[col][row].setEnabled(false);

        tvInfo.setText("Otvoreno polje. Sada igra drugi igrač.");
        switchPlayer();
        updateHeader();
    }

    private void guessColumn(int col) {
        if (columnSolved[col] || finalSolved) {
            return;
        }

        String guess = columnInputs[col].getText().toString().trim();
        String answer = columnAnswers[round - 1][col];

        if (guess.equalsIgnoreCase(answer)) {
            int points = calculateColumnPoints(col);
            addPoints(points);

            columnSolved[col] = true;
            columnSolutions[col].setText(answer);
            guessColumnButtons[col].setEnabled(false);

            for (int row = 0; row < 4; row++) {
                fieldButtons[col][row].setEnabled(false);
                if (!opened[col][row]) {
                    fieldButtons[col][row].setText(fields[round - 1][col][row]);
                }
            }

            tvInfo.setText("Tačno! Dobijeno bodova za kolonu: " + points);
        } else {
            tvInfo.setText("Netačno. Igra drugi igrač.");
            Toast.makeText(this, "Netačno rešenje kolone", Toast.LENGTH_SHORT).show();
            switchPlayer();
        }

        updateHeader();
    }

    private void guessFinal() {
        if (finalSolved) {
            return;
        }

        String guess = finalInput.getText().toString().trim();
        String answer = finalAnswers[round - 1];

        if (guess.equalsIgnoreCase(answer)) {
            int points = calculateFinalPoints();
            addPoints(points);

            finalSolved = true;
            btnGuessFinal.setEnabled(false);

            for (int col = 0; col < 4; col++) {
                columnSolutions[col].setText(columnAnswers[round - 1][col]);
                guessColumnButtons[col].setEnabled(false);

                for (int row = 0; row < 4; row++) {
                    fieldButtons[col][row].setText(fields[round - 1][col][row]);
                    fieldButtons[col][row].setEnabled(false);
                }
            }

            tvInfo.setText("Tačno konačno rešenje! Dobijeno bodova: " + points);
            finishRound();
        } else {
            tvInfo.setText("Netačno konačno rešenje. Igra drugi igrač.");
            Toast.makeText(this, "Netačno konačno rešenje", Toast.LENGTH_SHORT).show();
            switchPlayer();
        }

        updateHeader();
    }

    private int calculateColumnPoints(int col) {
        int unopened = 0;
        for (int row = 0; row < 4; row++) {
            if (!opened[col][row]) {
                unopened++;
            }
        }
        return 2 + unopened;
    }

    private int calculateFinalPoints() {
        int points = 7;

        for (int col = 0; col < 4; col++) {
            if (!columnSolved[col]) {
                int openedCount = 0;
                for (int row = 0; row < 4; row++) {
                    if (opened[col][row]) {
                        openedCount++;
                    }
                }

                if (openedCount == 0) {
                    points += 6;
                } else {
                    points += calculateColumnPoints(col);
                }
            }
        }

        return points;
    }

    private void addPoints(int points) {
        if (currentPlayer == 1) {
            player1Score += points;
        } else {
            player2Score += points;
        }
    }

    private void switchPlayer() {
        currentPlayer = currentPlayer == 1 ? 2 : 1;
    }

    private void startTimer() {
        timer = new CountDownTimer(120000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                long minutes = seconds / 60;
                long restSeconds = seconds % 60;

                tvTimer.setText(String.format("%02d:%02d", minutes, restSeconds));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("00:00");
                tvInfo.setText("Vreme je isteklo.");
                finishRound();
            }
        };

        timer.start();
    }

    private void finishRound() {
        if (timer != null) {
            timer.cancel();
        }

        for (int col = 0; col < 4; col++) {
            guessColumnButtons[col].setEnabled(false);
            for (int row = 0; row < 4; row++) {
                fieldButtons[col][row].setEnabled(false);
            }
        }

        btnGuessFinal.setEnabled(false);
        btnNextRound.setVisibility(View.VISIBLE);

        if (round == 1) {
            btnNextRound.setText("Sledeća runda");
        } else {
            btnNextRound.setText("Završi igru");
        }

        updateHeader();
    }

    private void showEndGame() {
        String winner;

        if (player1Score > player2Score) {
            winner = "Pobednik je igrač 1!";
        } else if (player2Score > player1Score) {
            winner = "Pobednik je igrač 2!";
        } else {
            winner = "Nerešeno je!";
        }

        tvInfo.setText("Kraj igre. " + winner);
        btnNextRound.setVisibility(View.GONE);
        Toast.makeText(this, winner, Toast.LENGTH_LONG).show();
    }

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        tvPlayer.setText("Na potezu: igrač " + currentPlayer);
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }
}