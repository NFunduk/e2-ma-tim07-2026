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

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class SpojniceActivity extends AppCompatActivity {

    // Header views
    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo, tvCriteria;

    // Bodovi kartice
    private TextView tvRoundScore1, tvRoundScore2, tvConnected;

    // Dugme dalje
    private Button btnNextRound;

    // Lijeva i desna kolona dugmadi
    private Button[] leftButtons = new Button[5];
    private Button[] rightButtons = new Button[5];

    // Strelice u sredini
    private TextView[] arrows = new TextView[5];

    // Timer
    private CountDownTimer timer;

    // ---- Stanje igre ----
    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    // Koji lijevi je selektovan (-1 = niko)
    private int selectedLeft = -1;

    // Da li je svaki par spojen
    private boolean[] connected = new boolean[5];

    // Ko je spojio koji par (1 = igrac1, 2 = igrac2, 0 = niko)
    private int[] connectedBy = new int[5];

    // Tacni parovi: leftIndex -> rightIndex
    // Npr. leftItems[0] se para sa rightItems[correctPairs[0]]
    private final int[][] correctPairs = {
            {2, 0, 4, 1, 3},  // Runda 1
            {1, 3, 0, 4, 2}   // Runda 2
    };

    // Mokap podaci - lijeva kolona
    private final String[][] leftItems = {
            {"Madonna", "Eminem", "Beyoncé", "Michael Jackson", "Adele"},
            {"Picasso", "Da Vinci", "Van Gogh", "Monet", "Rembrandt"}
    };

    // Mokap podaci - desna kolona (izmješana)
    private final String[][] rightItems = {
            {"Halo", "Single Ladies", "Lose Yourself", "Like a Prayer", "Billie Jean"},
            {"Guernica", "The Starry Night", "Mona Lisa", "Water Lilies", "The Night Watch"}
    };

    // Kriterijumi po rundi
    private final String[] criteria = {
            "Povežite izvođače sa njihovim pjesmama",
            "Povežite slikare sa njihovim djelima"
    };

    // Da li je runda završena
    private boolean roundFinished = false;

    // Broj spojenih u ovoj rundi (igrac1 + igrac2)
    private int connectedCount = 0;

    // Bodovi u ovoj rundi po igracu
    private int roundScore1 = 0;
    private int roundScore2 = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_spojnice);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.spojniceRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        connectViews();
        setupClicks();
        setupBottomNavigation();
        startRound();
    }

    private void connectViews() {
        tvRound = findViewById(R.id.tvRound);
        tvPlayer = findViewById(R.id.tvPlayer);
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
        tvInfo = findViewById(R.id.tvInfo);
        tvCriteria = findViewById(R.id.tvCriteria);
        tvRoundScore1 = findViewById(R.id.tvRoundScore1);
        tvRoundScore2 = findViewById(R.id.tvRoundScore2);
        tvConnected = findViewById(R.id.tvConnected);
        btnNextRound = findViewById(R.id.btnNextRound);

        leftButtons[0] = findViewById(R.id.btnLeft1);
        leftButtons[1] = findViewById(R.id.btnLeft2);
        leftButtons[2] = findViewById(R.id.btnLeft3);
        leftButtons[3] = findViewById(R.id.btnLeft4);
        leftButtons[4] = findViewById(R.id.btnLeft5);

        rightButtons[0] = findViewById(R.id.btnRight1);
        rightButtons[1] = findViewById(R.id.btnRight2);
        rightButtons[2] = findViewById(R.id.btnRight3);
        rightButtons[3] = findViewById(R.id.btnRight4);
        rightButtons[4] = findViewById(R.id.btnRight5);

        arrows[0] = findViewById(R.id.tvArrow1);
        arrows[1] = findViewById(R.id.tvArrow2);
        arrows[2] = findViewById(R.id.tvArrow3);
        arrows[3] = findViewById(R.id.tvArrow4);
        arrows[4] = findViewById(R.id.tvArrow5);
    }

    private void setupClicks() {
        // Klik na lijevi pojam - selektuj ga
        for (int i = 0; i < 5; i++) {
            int index = i;
            leftButtons[i].setOnClickListener(v -> onLeftClick(index));
        }

        // Klik na desni pojam - pokusaj spajanje
        for (int i = 0; i < 5; i++) {
            int index = i;
            rightButtons[i].setOnClickListener(v -> onRightClick(index));
        }

        btnNextRound.setOnClickListener(v -> {
            if (roundFinished) {
                if (round == 1) {
                    round = 2;
                    currentPlayer = 2;
                    startRound();
                } else {
                    showEndGame();
                }
            }
        });
    }

    private void onLeftClick(int index) {
        if (roundFinished || connected[index]) return;

        // Poništi prethodnu selekciju
        if (selectedLeft != -1) {
            leftButtons[selectedLeft].setBackgroundTintList(
                    getColorStateList(R.color.white));
        }

        selectedLeft = index;

        // Oznaci kao selektovan (plava boja)
        leftButtons[index].setBackgroundTintList(
                getColorStateList(R.color.blue));

        tvInfo.setText("Odabrano: \"" + leftItems[round - 1][index] + "\". Sada klikni na odgovarajući par desno.");
    }

    private void onRightClick(int rightIndex) {
        if (roundFinished) return;

        if (selectedLeft == -1) {
            Toast.makeText(this, "Prvo klikni na pojam s lijeve strane!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Provjeri da li je desna strana vec spojena
        for (int i = 0; i < 5; i++) {
            if (connected[i] && correctPairs[round - 1][i] == rightIndex) {
                Toast.makeText(this, "Ovaj par je već spojen!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Provjeri da li je tacan par
        if (correctPairs[round - 1][selectedLeft] == rightIndex) {
            // TACNO!
            connected[selectedLeft] = true;
            connectedBy[selectedLeft] = currentPlayer;
            connectedCount++;

            // Oznaci kao tacno - zelena boja
            leftButtons[selectedLeft].setBackgroundTintList(
                    getColorStateList(android.R.color.holo_green_dark));
            rightButtons[rightIndex].setBackgroundTintList(
                    getColorStateList(android.R.color.holo_green_dark));
            arrows[selectedLeft].setText("✓");
            arrows[selectedLeft].setTextColor(getColor(android.R.color.holo_green_dark));

            // Onemogući spojena dugmad
            leftButtons[selectedLeft].setEnabled(false);
            rightButtons[rightIndex].setEnabled(false);

            // Dodaj bodove
            if (currentPlayer == 1) {
                roundScore1 += 2;
                player1Score += 2;
            } else {
                roundScore2 += 2;
                player2Score += 2;
            }

            selectedLeft = -1;
            tvInfo.setText("Tačno! +2 boda za Igrača " + currentPlayer);

            updateScoreViews();

            // Provjeri da li su svi spojeni
            if (connectedCount == 5) {
                finishRound();
            }

        } else {
            // NETACNO - poništi selekciju
            leftButtons[selectedLeft].setBackgroundTintList(
                    getColorStateList(R.color.white));
            selectedLeft = -1;
            tvInfo.setText("Netačno! Pokušaj ponovo.");
            Toast.makeText(this, "Netačan par!", Toast.LENGTH_SHORT).show();
        }

        updateHeader();
    }

    private void startRound() {
        if (timer != null) timer.cancel();

        // Reset stanja
        selectedLeft = -1;
        connectedCount = 0;
        roundFinished = false;
        roundScore1 = 0;
        roundScore2 = 0;

        for (int i = 0; i < 5; i++) {
            connected[i] = false;
            connectedBy[i] = 0;
        }

        btnNextRound.setVisibility(View.GONE);

        // Postavi tekst i boje na dugmad
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(leftItems[round - 1][i]);
            leftButtons[i].setEnabled(true);
            leftButtons[i].setBackgroundTintList(getColorStateList(R.color.white));

            rightButtons[i].setText(rightItems[round - 1][i]);
            rightButtons[i].setEnabled(true);
            rightButtons[i].setBackgroundTintList(getColorStateList(R.color.petal));

            arrows[i].setText("→");
            arrows[i].setTextColor(getColor(R.color.dark_blue));
        }

        tvCriteria.setText(criteria[round - 1]);
        tvInfo.setText("Klikni na pojam lijevo, pa na odgovarajući pojam desno.");

        updateScoreViews();
        updateHeader();
        startTimer();
    }

    private void startTimer() {
        timer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvTimer.setText(String.format("00:%02d", seconds));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("00:00");
                tvInfo.setText("Vreme je isteklo!");
                finishRound();
            }
        };
        timer.start();
    }

    private void finishRound() {
        if (timer != null) timer.cancel();
        roundFinished = true;

        // Onemogući sva dugmad
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setEnabled(false);
            rightButtons[i].setEnabled(false);
        }

        btnNextRound.setVisibility(View.VISIBLE);

        if (round == 1) {
            btnNextRound.setText("Sljedeća runda →");
        } else {
            btnNextRound.setText("Završi igru");
        }

        tvInfo.setText("Runda završena! Igrač 1: " + roundScore1 + " bod. | Igrač 2: " + roundScore2 + " bod.");
        updateHeader();
    }

    private void showEndGame() {
        String winner;
        if (player1Score > player2Score) {
            winner = "Pobednik spojnica je Igrač 1!";
        } else if (player2Score > player1Score) {
            winner = "Pobednik spojnica je Igrač 2!";
        } else {
            winner = "Spojnice su nerješene!";
        }

        Toast.makeText(this, winner + " Slijedi: Ko zna zna.", Toast.LENGTH_LONG).show();

        // TODO: navigacija na sljedecu igru
        // Intent intent = new Intent(SpojniceActivity.this, KoZnaZnaActivity.class);
        // startActivity(intent);
        finish();
    }

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        tvPlayer.setText("Na potezu: Igrač " + currentPlayer);
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private void updateScoreViews() {
        tvRoundScore1.setText(roundScore1 + " bod.");
        tvRoundScore2.setText(roundScore2 + " bod.");
        tvConnected.setText(connectedCount + "/5");
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.play);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.home) {
                startActivity(new Intent(SpojniceActivity.this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.play) {
                return true;
            } else if (id == R.id.profile) {
                startActivity(new Intent(SpojniceActivity.this, ProfileActivity.class));
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