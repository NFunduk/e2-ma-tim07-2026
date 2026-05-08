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

public class SpojniceFragment extends Fragment {

    // Header views
    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo, tvCriteria;

    // Bodovi kartice
    private TextView tvRoundScore1, tvRoundScore2, tvConnected;

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

    private int selectedLeft = -1;
    private boolean[] connected = new boolean[5];
    private int[] connectedBy = new int[5];

    private final int[][] correctPairs = {
            {2, 0, 4, 1, 3},  // Runda 1
            {1, 3, 0, 4, 2}   // Runda 2
    };

    private final String[][] leftItems = {
            {"Madonna", "Eminem", "Beyoncé", "Michael Jackson", "Adele"},
            {"Picasso", "Da Vinci", "Van Gogh", "Monet", "Rembrandt"}
    };

    private final String[][] rightItems = {
            {"Halo", "Single Ladies", "Lose Yourself", "Like a Prayer", "Billie Jean"},
            {"Guernica", "The Starry Night", "Mona Lisa", "Water Lilies", "The Night Watch"}
    };

    private final String[] criteria = {
            "Povežite izvođače sa njihovim pjesmama",
            "Povežite slikare sa njihovim djelima"
    };

    private boolean roundFinished = false;
    private int connectedCount = 0;
    private int roundScore1 = 0;
    private int roundScore2 = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvRound       = view.findViewById(R.id.tvRound);
        tvPlayer      = view.findViewById(R.id.tvPlayer);
        tvTimer       = view.findViewById(R.id.tvTimer);
        tvScore       = view.findViewById(R.id.tvScore);
        tvInfo        = view.findViewById(R.id.tvInfo);
        tvCriteria    = view.findViewById(R.id.tvCriteria);
        tvRoundScore1 = view.findViewById(R.id.tvRoundScore1);
        tvRoundScore2 = view.findViewById(R.id.tvRoundScore2);
        tvConnected   = view.findViewById(R.id.tvConnected);

        leftButtons[0] = view.findViewById(R.id.btnLeft1);
        leftButtons[1] = view.findViewById(R.id.btnLeft2);
        leftButtons[2] = view.findViewById(R.id.btnLeft3);
        leftButtons[3] = view.findViewById(R.id.btnLeft4);
        leftButtons[4] = view.findViewById(R.id.btnLeft5);

        rightButtons[0] = view.findViewById(R.id.btnRight1);
        rightButtons[1] = view.findViewById(R.id.btnRight2);
        rightButtons[2] = view.findViewById(R.id.btnRight3);
        rightButtons[3] = view.findViewById(R.id.btnRight4);
        rightButtons[4] = view.findViewById(R.id.btnRight5);

        arrows[0] = view.findViewById(R.id.tvArrow1);
        arrows[1] = view.findViewById(R.id.tvArrow2);
        arrows[2] = view.findViewById(R.id.tvArrow3);
        arrows[3] = view.findViewById(R.id.tvArrow4);
        arrows[4] = view.findViewById(R.id.tvArrow5);

        setupClicks();
        startRound();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
    }

    private void setupClicks() {
        for (int i = 0; i < 5; i++) {
            int index = i;
            leftButtons[i].setOnClickListener(v -> onLeftClick(index));
        }
        for (int i = 0; i < 5; i++) {
            int index = i;
            rightButtons[i].setOnClickListener(v -> onRightClick(index));
        }
    }

    private void onLeftClick(int index) {
        if (roundFinished || connected[index]) return;

        if (selectedLeft != -1) {
            leftButtons[selectedLeft].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
        }

        selectedLeft = index;
        leftButtons[index].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), R.color.blue));

        tvInfo.setText("Odabrano: \"" + leftItems[round - 1][index] + "\". Sada klikni na odgovarajući par desno.");
    }

    private void onRightClick(int rightIndex) {
        if (roundFinished) return;

        if (selectedLeft == -1) {
            Toast.makeText(requireContext(), "Prvo klikni na pojam s lijeve strane!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Provjeri da li je desna strana vec spojena
        for (int i = 0; i < 5; i++) {
            if (connected[i] && correctPairs[round - 1][i] == rightIndex) {
                Toast.makeText(requireContext(), "Ovaj par je već spojen!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (correctPairs[round - 1][selectedLeft] == rightIndex) {
            // TACNO!
            connected[selectedLeft] = true;
            connectedBy[selectedLeft] = currentPlayer;
            connectedCount++;

            leftButtons[selectedLeft].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
            rightButtons[rightIndex].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
            arrows[selectedLeft].setText("✓");
            arrows[selectedLeft].setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));

            leftButtons[selectedLeft].setEnabled(false);
            rightButtons[rightIndex].setEnabled(false);

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

            if (connectedCount == 5) {
                finishRound();
            }

        } else {
            // NETACNO
            leftButtons[selectedLeft].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
            selectedLeft = -1;
            tvInfo.setText("Netačno! Pokušaj ponovo.");
            Toast.makeText(requireContext(), "Netačan par!", Toast.LENGTH_SHORT).show();
        }

        updateHeader();
    }

    private void startRound() {
        if (timer != null) timer.cancel();

        selectedLeft = -1;
        connectedCount = 0;
        roundFinished = false;
        roundScore1 = 0;
        roundScore2 = 0;

        for (int i = 0; i < 5; i++) {
            connected[i] = false;
            connectedBy[i] = 0;
        }

        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(leftItems[round - 1][i]);
            leftButtons[i].setEnabled(true);
            leftButtons[i].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));

            rightButtons[i].setText(rightItems[round - 1][i]);
            rightButtons[i].setEnabled(true);
            rightButtons[i].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.petal));

            arrows[i].setText("→");
            arrows[i].setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.dark_blue));
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

        for (int i = 0; i < 5; i++) {
            leftButtons[i].setEnabled(false);
            rightButtons[i].setEnabled(false);
        }

        updateHeader();

        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvInfo.setText("Runda završena! Sljedeća za: " + seconds + "s");
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
    }

    private void showEndGame() {
        String winner;
        if (player1Score > player2Score) {
            winner = "Pobjednik spojnica je Igrač 1!";
        } else if (player2Score > player1Score) {
            winner = "Pobjednik spojnica je Igrač 2!";
        } else {
            winner = "Spojnice su nerješene!";
        }

        Toast.makeText(requireContext(), winner, Toast.LENGTH_LONG).show();

        NavHostFragment.findNavController(this)
                .navigate(R.id.action_spojnice_to_associations);
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
}