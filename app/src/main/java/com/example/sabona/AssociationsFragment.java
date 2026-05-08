package com.example.sabona;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class AssociationsFragment extends Fragment {

    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo;
    private Button[][] fieldButtons = new Button[4][4];
    private TextView[] columnSolutions = new TextView[4];
    private EditText[] columnInputs = new EditText[4];
    private Button[] guessColumnButtons = new Button[4];
    private EditText finalInput;
    private Button btnGuessFinal;

    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    private boolean[][] opened = new boolean[4][4];
    private boolean[] columnSolved = new boolean[4];
    private boolean finalSolved = false;
    private boolean fieldOpenedThisTurn = false;
    private boolean roundFinished = false;

    private CountDownTimer timer;

    private final String[][][] fields = {
            {
                    {"Lav", "Tigar", "Vuk", "Medved"},
                    {"Jabuka", "Kruška", "Šljiva", "Breskva"},
                    {"Ruža", "Lala", "Hrast", "Bor"},
                    {"Sava", "Dunav", "Tisa", "Morava"}
            },
            {
                    {"Fudbal", "Tenis", "Košarka", "Odbojka"},
                    {"Zlato", "Srebro", "Bronza", "Pehar"},
                    {"Golman", "Kapiten", "Rezerva", "Igrač"},
                    {"Sudija", "Pravila", "Faul", "Zapisnik"}
            }
    };

    private final String[][] columnAnswers = {
            {"Zivotinje", "Voce", "Biljke", "Reke"},
            {"Sportovi", "Nagrade", "Tim", "Sudjenje"}
    };

    private final String[] finalAnswers = {
            "Priroda",
            "Takmicenje"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_associations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        connectViews(view);
        setupClicks();
        startRound();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
    }

    private void connectViews(View view) {
        tvRound = view.findViewById(R.id.tvRound);
        tvPlayer = view.findViewById(R.id.tvPlayer);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvScore = view.findViewById(R.id.tvScore);
        tvInfo = view.findViewById(R.id.tvInfo);

        fieldButtons[0][0] = view.findViewById(R.id.a1);
        fieldButtons[0][1] = view.findViewById(R.id.a2);
        fieldButtons[0][2] = view.findViewById(R.id.a3);
        fieldButtons[0][3] = view.findViewById(R.id.a4);

        fieldButtons[1][0] = view.findViewById(R.id.b1);
        fieldButtons[1][1] = view.findViewById(R.id.b2);
        fieldButtons[1][2] = view.findViewById(R.id.b3);
        fieldButtons[1][3] = view.findViewById(R.id.b4);

        fieldButtons[2][0] = view.findViewById(R.id.c1);
        fieldButtons[2][1] = view.findViewById(R.id.c2);
        fieldButtons[2][2] = view.findViewById(R.id.c3);
        fieldButtons[2][3] = view.findViewById(R.id.c4);

        fieldButtons[3][0] = view.findViewById(R.id.d1);
        fieldButtons[3][1] = view.findViewById(R.id.d2);
        fieldButtons[3][2] = view.findViewById(R.id.d3);
        fieldButtons[3][3] = view.findViewById(R.id.d4);

        columnSolutions[0] = view.findViewById(R.id.solutionA);
        columnSolutions[1] = view.findViewById(R.id.solutionB);
        columnSolutions[2] = view.findViewById(R.id.solutionC);
        columnSolutions[3] = view.findViewById(R.id.solutionD);

        columnInputs[0] = view.findViewById(R.id.inputA);
        columnInputs[1] = view.findViewById(R.id.inputB);
        columnInputs[2] = view.findViewById(R.id.inputC);
        columnInputs[3] = view.findViewById(R.id.inputD);

        guessColumnButtons[0] = view.findViewById(R.id.btnGuessA);
        guessColumnButtons[1] = view.findViewById(R.id.btnGuessB);
        guessColumnButtons[2] = view.findViewById(R.id.btnGuessC);
        guessColumnButtons[3] = view.findViewById(R.id.btnGuessD);

        finalInput = view.findViewById(R.id.inputFinal);
        btnGuessFinal = view.findViewById(R.id.btnGuessFinal);
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
    }

    private void startRound() {
        if (timer != null) timer.cancel();

        finalSolved = false;
        fieldOpenedThisTurn = false;
        roundFinished = false;

        for (int col = 0; col < 4; col++) {
            columnSolved[col] = false;
            columnSolutions[col].setText("?");
            columnInputs[col].setText("");
            columnInputs[col].setEnabled(true);
            guessColumnButtons[col].setEnabled(true);

            for (int row = 0; row < 4; row++) {
                opened[col][row] = false;
                fieldButtons[col][row].setText("?");
                fieldButtons[col][row].setEnabled(true);
            }
        }

        finalInput.setText("");
        finalInput.setEnabled(true);
        btnGuessFinal.setEnabled(true);

        tvInfo.setText("Otvori jedno polje, pa pogodi kolonu ili konačno rešenje.");
        updateHeader();
        startTimer();
    }

    private void openField(int col, int row) {
        if (opened[col][row] || columnSolved[col] || finalSolved || fieldOpenedThisTurn || roundFinished) {
            return;
        }

        opened[col][row] = true;
        fieldOpenedThisTurn = true;

        fieldButtons[col][row].setText(fields[round - 1][col][row]);
        disableFieldOpening();

        tvInfo.setText("Otvoreno polje. Sada možeš da pogađaš.");
        updateHeader();
    }

    private void passTurn() {
        switchPlayer();
        fieldOpenedThisTurn = false;
        enableFieldOpening();
        tvInfo.setText("Na potezu je igrač " + currentPlayer + ". Otvori jedno polje.");
        updateHeader();
    }

    private void guessColumn(int col) {
        if (columnSolved[col] || finalSolved || roundFinished) return;

        if (!canGuessNow()) {
            Toast.makeText(requireContext(), "Prvo otvori jedno polje.", Toast.LENGTH_SHORT).show();
            return;
        }

        String guess = columnInputs[col].getText().toString().trim();
        String answer = columnAnswers[round - 1][col];

        if (guess.isEmpty()) {
            Toast.makeText(requireContext(), "Unesi rešenje kolone", Toast.LENGTH_SHORT).show();
            return;
        }

        if (guess.equalsIgnoreCase(answer)) {
            int points = calculateColumnPoints(col);
            addPoints(points);
            revealColumn(col);
            tvInfo.setText("Tačno! Dobijeno bodova za kolonu: " + points);
        } else {
            tvInfo.setText("Netačno. Igra drugi igrač.");
            Toast.makeText(requireContext(), "Netačno rešenje kolone", Toast.LENGTH_SHORT).show();
            passTurn();
        }

        updateHeader();
    }

    private void guessFinal() {
        if (finalSolved || roundFinished) return;

        if (!canGuessNow()) {
            Toast.makeText(requireContext(), "Prvo otvori jedno polje.", Toast.LENGTH_SHORT).show();
            return;
        }

        String guess = finalInput.getText().toString().trim();
        String answer = finalAnswers[round - 1];

        if (guess.isEmpty()) {
            Toast.makeText(requireContext(), "Unesi konačno rešenje", Toast.LENGTH_SHORT).show();
            return;
        }

        if (guess.equalsIgnoreCase(answer)) {
            int points = calculateFinalPoints();
            addPoints(points);

            finalSolved = true;
            revealAllAnswers();

            tvInfo.setText("Tačno konačno rešenje! Dobijeno bodova: " + points);
            finishRound();
        } else {
            tvInfo.setText("Netačno konačno rešenje. Igra drugi igrač.");
            Toast.makeText(requireContext(), "Netačno konačno rešenje", Toast.LENGTH_SHORT).show();
            passTurn();
        }

        updateHeader();
    }

    private void disableFieldOpening() {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                if (!opened[col][row] && !columnSolved[col]) {
                    fieldButtons[col][row].setEnabled(false);
                }
            }
        }
    }

    private void enableFieldOpening() {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                if (!opened[col][row] && !columnSolved[col] && !finalSolved && !roundFinished) {
                    fieldButtons[col][row].setEnabled(true);
                }
            }
        }
    }

    private void revealColumn(int col) {
        columnSolved[col] = true;
        columnSolutions[col].setText(columnAnswers[round - 1][col]);
        guessColumnButtons[col].setEnabled(false);
        columnInputs[col].setEnabled(false);

        for (int row = 0; row < 4; row++) {
            fieldButtons[col][row].setText(fields[round - 1][col][row]);
            fieldButtons[col][row].setEnabled(false);
        }
    }

    private void revealAllAnswers() {
        for (int col = 0; col < 4; col++) {
            columnSolutions[col].setText(columnAnswers[round - 1][col]);
            guessColumnButtons[col].setEnabled(false);
            columnInputs[col].setEnabled(false);

            for (int row = 0; row < 4; row++) {
                fieldButtons[col][row].setText(fields[round - 1][col][row]);
                fieldButtons[col][row].setEnabled(false);
            }
        }

        finalInput.setText(finalAnswers[round - 1]);
        finalInput.setEnabled(false);
        btnGuessFinal.setEnabled(false);
    }

    private int calculateColumnPoints(int col) {
        int unopened = 0;

        for (int row = 0; row < 4; row++) {
            if (!opened[col][row]) unopened++;
        }

        return 2 + unopened;
    }

    private int calculateFinalPoints() {
        int points = 7;

        for (int col = 0; col < 4; col++) {
            if (columnSolved[col]) continue;

            int openedCount = 0;
            for (int row = 0; row < 4; row++) {
                if (opened[col][row]) openedCount++;
            }

            if (openedCount == 0) {
                points += 6;
            } else {
                points += calculateColumnPoints(col);
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
                tvInfo.setText("Vreme je isteklo. Rešenja su otkrivena.");
                revealAllAnswers();
                finishRound();
            }
        };

        timer.start();
    }

    private void finishRound() {
        if (timer != null) timer.cancel();

        roundFinished = true;
        fieldOpenedThisTurn = false;

        for (int col = 0; col < 4; col++) {
            guessColumnButtons[col].setEnabled(false);
            columnInputs[col].setEnabled(false);

            for (int row = 0; row < 4; row++) {
                fieldButtons[col][row].setEnabled(false);
            }
        }

        finalInput.setEnabled(false);
        btnGuessFinal.setEnabled(false);

        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvInfo.setText("Runda završena! Sledeća za: " + seconds + "s");
            }

            @Override
            public void onFinish() {
                if (round == 1) {
                    round = 2;
                    currentPlayer = 2;
                    startRound();
                } else {
                    showEndGame();
                }
            }
        }.start();

        updateHeader();
    }

    private void showEndGame() {
        String winner;

        if (player1Score > player2Score) {
            winner = "Pobednik asocijacija je igrač 1!";
        } else if (player2Score > player1Score) {
            winner = "Pobednik asocijacija je igrač 2!";
        } else {
            winner = "Asocijacije su nerešene!";
        }

        Toast.makeText(requireContext(), winner + " Sledi igra Korak po korak.", Toast.LENGTH_LONG).show();

        NavHostFragment.findNavController(this)
                .navigate(R.id.action_associations_to_skocko);
    }

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        tvPlayer.setText("Na potezu: igrač " + currentPlayer);
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private boolean hasOpenableField() {
        for (int col = 0; col < 4; col++) {
            if (!columnSolved[col]) {
                for (int row = 0; row < 4; row++) {
                    if (!opened[col][row]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canGuessNow() {
        return fieldOpenedThisTurn || !hasOpenableField();
    }
}