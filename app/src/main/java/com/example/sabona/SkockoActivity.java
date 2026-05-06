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

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Arrays;
import java.util.Random;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import android.widget.ImageButton;

import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;
import android.text.style.AbsoluteSizeSpan;
import androidx.core.content.ContextCompat;

public class SkockoActivity extends AppCompatActivity {

    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo, currentGuess;
    private TextView[] rows = new TextView[6];
    private ImageButton[] symbolButtons = new ImageButton[6];
    private Button btnCheck, btnClear, btnNextSkocko;

    private final String[] symbols = {"☻", "■", "●", "♥", "▲", "★"};
    private String[] secret = new String[4];
    private String[] guess = new String[4];

    private int guessIndex = 0;
    private int attempt = 0;
    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    private boolean opponentChance = false;
    private boolean roundFinished = false;

    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_skocko);

        connectViews();
        setupClicks();
        setupBottomNavigation();
        startRound();
    }

    private void connectViews() {
        tvRound = findViewById(R.id.tvSkockoRound);
        tvPlayer = findViewById(R.id.tvSkockoPlayer);
        tvTimer = findViewById(R.id.tvSkockoTimer);
        tvScore = findViewById(R.id.tvSkockoScore);
        tvInfo = findViewById(R.id.tvSkockoInfo);
        currentGuess = findViewById(R.id.currentGuess);

        rows[0] = findViewById(R.id.row1);
        rows[1] = findViewById(R.id.row2);
        rows[2] = findViewById(R.id.row3);
        rows[3] = findViewById(R.id.row4);
        rows[4] = findViewById(R.id.row5);
        rows[5] = findViewById(R.id.row6);

        symbolButtons[0] = findViewById(R.id.symbol1);
        symbolButtons[1] = findViewById(R.id.symbol2);
        symbolButtons[2] = findViewById(R.id.symbol3);
        symbolButtons[3] = findViewById(R.id.symbol4);
        symbolButtons[4] = findViewById(R.id.symbol5);
        symbolButtons[5] = findViewById(R.id.symbol6);

        btnCheck = findViewById(R.id.btnCheck);
        btnClear = findViewById(R.id.btnClear);
        btnNextSkocko = findViewById(R.id.btnNextSkocko);
    }

    private void setupClicks() {
        for (int i = 0; i < symbolButtons.length; i++) {
            int index = i;
            symbolButtons[i].setOnClickListener(v -> addSymbol(symbols[index]));
        }

        btnClear.setOnClickListener(v -> clearGuess());
        btnCheck.setOnClickListener(v -> checkGuess());

        btnNextSkocko.setOnClickListener(v -> {
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

        generateSecret();

        attempt = 0;
        guessIndex = 0;
        opponentChance = false;
        roundFinished = false;
        guess = new String[4];

        for (TextView row : rows) {
            row.setText("_  _  _  _     |     ○ ○ ○ ○");
            row.setTextSize(18);
        }

        currentGuess.setText("_  _  _  _");
        btnNextSkocko.setVisibility(View.GONE);
        enableGame(true);

        tvInfo.setText("Igrač " + currentPlayer + " ima 6 pokušaja da pogodi kombinaciju.");
        updateHeader();
        startTimer(30000);
    }

    private void generateSecret() {
        Random random = new Random();

        for (int i = 0; i < 4; i++) {
            secret[i] = symbols[random.nextInt(symbols.length)];
        }
    }

    private void addSymbol(String symbol) {
        if (roundFinished) {
            return;
        }

        if (guessIndex >= 4) {
            Toast.makeText(this, "Već su izabrana 4 znaka.", Toast.LENGTH_SHORT).show();
            return;
        }

        guess[guessIndex] = symbol;
        guessIndex++;
        updateCurrentGuess();
    }

    private void clearGuess() {
        guess = new String[4];
        guessIndex = 0;
        updateCurrentGuess();
    }

    private void updateCurrentGuess() {
        currentGuess.setText(buildIconsText(guess, true));
    }

    private void checkGuess() {
        if (roundFinished) {
            return;
        }

        if (guessIndex < 4) {
            Toast.makeText(this, "Izaberi 4 znaka.", Toast.LENGTH_SHORT).show();
            return;
        }

        int correctPlace = countCorrectPlace();
        int correctSymbol = countCorrectSymbol() - correctPlace;

        if (opponentChance) {
            showAttemptResult(correctPlace, correctSymbol);

            if (correctPlace == 4) {
                addPoints(10);
                tvInfo.setText("Protivnik je pogodio i osvojio 10 bodova!");
            } else {
                tvInfo.setText("Protivnik nije pogodio. Rešenje je: " + secretText());
            }

            finishRound();
            return;
        }

        setAttemptRow(rows[attempt], correctPlace, correctSymbol);

        if (correctPlace == 4) {
            int points = calculatePoints(attempt + 1);
            addPoints(points);
            tvInfo.setText("Tačno! Igrač " + currentPlayer + " osvaja " + points + " bodova.");
            finishRound();
            return;
        }

        attempt++;
        clearGuess();

        if (attempt == 6) {
            startOpponentChance();
        } else {
            tvInfo.setText("Pokušaj " + (attempt + 1) + "/6.");
        }

        updateHeader();
    }

    private int countCorrectPlace() {
        int count = 0;

        for (int i = 0; i < 4; i++) {
            if (guess[i].equals(secret[i])) {
                count++;
            }
        }

        return count;
    }

    private int countCorrectSymbol() {
        int count = 0;
        boolean[] usedSecret = new boolean[4];
        boolean[] usedGuess = new boolean[4];

        for (int i = 0; i < 4; i++) {
            if (guess[i].equals(secret[i])) {
                count++;
                usedSecret[i] = true;
                usedGuess[i] = true;
            }
        }

        for (int i = 0; i < 4; i++) {
            if (usedGuess[i]) continue;

            for (int j = 0; j < 4; j++) {
                if (!usedSecret[j] && guess[i].equals(secret[j])) {
                    count++;
                    usedSecret[j] = true;
                    break;
                }
            }
        }

        return count;
    }

    private void startOpponentChance() {
        if (timer != null) {
            timer.cancel();
        }

        opponentChance = true;
        switchPlayer();
        clearGuess();

        tvInfo.setText("Protivnik ima 10 sekundi za jedan pokušaj i 10 bodova.");
        updateHeader();
        startTimer(10000);
    }

    private void showAttemptResult(int correctPlace, int correctSymbol) {
        tvInfo.setText("Rezultat: " + correctPlace + " tačno mesto, " + correctSymbol + " znak.");
    }

    private void setAttemptRow(TextView row, int correctPlace, int correctSymbol) {
        String text = "●  ●  ●  ●     |     ● ● ● ●";
        SpannableString spannable = new SpannableString(text);

        int[] iconPositions = {0, 3, 6, 9};
        int[] circlePositions = {21, 23, 25, 27};

        for (int i = 0; i < 4; i++) {
            Drawable drawable = ContextCompat.getDrawable(this, getIconForSymbol(guess[i]));
            if (drawable != null) {
                drawable.setBounds(0, 0, 78, 78);

                spannable.setSpan(
                        new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                        iconPositions[i],
                        iconPositions[i] + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        for (int i = 0; i < 4; i++) {
            int color;

            if (i < correctPlace) {
                color = Color.RED;          // tačno mesto
            } else if (i < correctPlace + correctSymbol) {
                color = Color.YELLOW;       // pogođen znak, pogrešno mesto
            } else {
                color = Color.rgb(7, 28, 95); // ništa nije pogođeno
            }

            spannable.setSpan(
                    new ForegroundColorSpan(color),
                    circlePositions[i],
                    circlePositions[i] + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            spannable.setSpan(
                    new AbsoluteSizeSpan(28, true),
                    circlePositions[i],
                    circlePositions[i] + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        row.setText(spannable);
    }

    private int calculatePoints(int attemptNumber) {
        if (attemptNumber <= 2) {
            return 20;
        } else if (attemptNumber <= 4) {
            return 15;
        } else {
            return 10;
        }
    }

    private void addPoints(int points) {
        if (currentPlayer == 1) {
            player1Score += points;
        } else {
            player2Score += points;
        }
    }

    private void finishRound() {
        if (timer != null) {
            timer.cancel();
        }

        roundFinished = true;
        enableGame(false);
        currentGuess.setText(buildSolutionText());

        btnNextSkocko.setVisibility(View.VISIBLE);

        if (round == 1) {
            btnNextSkocko.setText("Sledeća runda");
        } else {
            btnNextSkocko.setText("Završi igru");
        }

        updateHeader();
    }

    private SpannableString buildSolutionText() {
        String text = "Rešenje: ●  ●  ●  ●";


        SpannableString spannable = new SpannableString(text);

        spannable.setSpan(
                new AbsoluteSizeSpan(20, true),
                0,
                8,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        int[] positions = {9, 12, 15, 18};

        for (int i = 0; i < 4; i++) {
            Drawable drawable = ContextCompat.getDrawable(this, getIconForSymbol(secret[i]));
            if (drawable != null) {
                drawable.setBounds(0, 0, 64, 64);

                spannable.setSpan(
                        new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                        positions[i],
                        positions[i] + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        return spannable;
    }

    private void enableGame(boolean enabled) {
        for (ImageButton b : symbolButtons) {
            b.setEnabled(enabled);
        }

        btnCheck.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    private void startTimer(long duration) {
        if (timer != null) {
            timer.cancel();
        }

        timer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvTimer.setText("00:" + String.format("%02d", seconds));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("00:00");

                if (opponentChance) {
                    tvInfo.setText("Vreme je isteklo. Rešenje je: " + secretText());
                    finishRound();
                } else {
                    startOpponentChance();
                }
            }
        };

        timer.start();
    }

    private String guessText() {
        return Arrays.toString(guess)
                .replace("[", "")
                .replace("]", "")
                .replace(",", " ");
    }

    private String secretText() {
        return Arrays.toString(secret)
                .replace("[", "")
                .replace("]", "")
                .replace(",", " ");
    }

    private void switchPlayer() {
        currentPlayer = currentPlayer == 1 ? 2 : 1;
    }

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        tvPlayer.setText("Na potezu: igrač " + currentPlayer);
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private void showEndGame() {
        String winner;

        if (player1Score > player2Score) {
            winner = "Pobednik igre Skočko je igrač 1!";
        } else if (player2Score > player1Score) {
            winner = "Pobednik igre Skočko je igrač 2!";
        } else {
            winner = "Skočko je nerešen!";
        }

        //tvInfo.setText(winner);
        //Toast.makeText(this, winner, Toast.LENGTH_LONG).show();
        //btnNextSkocko.setVisibility(View.GONE);

        tvInfo.setText(winner);
        Toast.makeText(this, winner + " Sledi Korak po korak!", Toast.LENGTH_LONG).show();
        btnNextSkocko.setText("Nastavi ");
        btnNextSkocko.setVisibility(View.VISIBLE);
        btnNextSkocko.setOnClickListener(v -> {
            startActivity(new Intent(SkockoActivity.this, KorakPoKorakActivity.class));
            finish();
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setSelectedItemId(R.id.play);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.home) {
                startActivity(new Intent(SkockoActivity.this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.play) {
                return true;
            } else if (id == R.id.profile) {
                startActivity(new Intent(SkockoActivity.this, ProfileActivity.class));
                return true;
            } else if (id == R.id.rank) {
                return true;
            } else if (id == R.id.friends) {
                return true;
            }

            return false;
        });
    }


    private int getIconForSymbol(String symbol) {
        switch (symbol) {
            case "☻":
                return R.drawable.ic_skocko;
            case "■":
                return R.drawable.ic_square;
            case "●":
                return R.drawable.ic_circle;
            case "♥":
                return R.drawable.ic_heart;
            case "▲":
                return R.drawable.ic_triangle;
            case "★":
                return R.drawable.ic_star;
            default:
                return R.drawable.ic_circle;
        }
    }


    private SpannableString buildIconsText(String[] values, boolean showEmpty) {
        String text = "●  ●  ●  ●";
        SpannableString spannable = new SpannableString(text);

        int[] positions = {0, 3, 6, 9};

        for (int i = 0; i < 4; i++) {
            int start = positions[i];

            if (values[i] == null) {
                if (showEmpty) {
                    spannable.setSpan(
                            new AbsoluteSizeSpan(24, true),
                            start,
                            start + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
                continue;
            }

            Drawable drawable = ContextCompat.getDrawable(this, getIconForSymbol(values[i]));
            if (drawable != null) {
                drawable.setBounds(0, 0, 62, 62);

                ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
                spannable.setSpan(
                        imageSpan,
                        start,
                        start + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        return spannable;
    }
}