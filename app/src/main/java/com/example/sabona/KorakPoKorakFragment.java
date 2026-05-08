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

public class KorakPoKorakFragment extends Fragment {

    private TextView tvKorakRound, tvKorakPlayer, tvKorakTimer, tvKorakScore;
    private TextView tvKorakInfo, tvCurrentPoints;
    private TextView tvStep1, tvStep2, tvStep3, tvStep4, tvStep5, tvStep6, tvStep7;
    private EditText etKorakAnswer;
    //private Button btnKorakGuess, btnKorakNext;
    private Button btnKorakGuess;

    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int currentStep = 0;
    private boolean roundFinished = false;

    private CountDownTimer timer;

    private final String[][] steps = {
            {
                    "Osnovan 1687. godine",
                    "Nalazi se u Engleskoj",
                    "Jedna od najstarijih na svetu",
                    "Poznata po Njutnovom stablu jabuke",
                    "Smestena u Kembridžu",
                    "Ivy League",
                    "Čuveni univerzitet u UK"
            },
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_korak_po_korak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvKorakRound    = view.findViewById(R.id.tvKorakRound);
        tvKorakPlayer   = view.findViewById(R.id.tvKorakPlayer);
        tvKorakTimer    = view.findViewById(R.id.tvKorakTimer);
        tvKorakScore    = view.findViewById(R.id.tvKorakScore);
        tvKorakInfo     = view.findViewById(R.id.tvKorakInfo);
        tvCurrentPoints = view.findViewById(R.id.tvCurrentPoints);
        tvStep1         = view.findViewById(R.id.tvStep1);
        tvStep2         = view.findViewById(R.id.tvStep2);
        tvStep3         = view.findViewById(R.id.tvStep3);
        tvStep4         = view.findViewById(R.id.tvStep4);
        tvStep5         = view.findViewById(R.id.tvStep5);
        tvStep6         = view.findViewById(R.id.tvStep6);
        tvStep7         = view.findViewById(R.id.tvStep7);
        etKorakAnswer   = view.findViewById(R.id.etKorakAnswer);
        btnKorakGuess   = view.findViewById(R.id.btnKorakGuess);
       // btnKorakNext    = view.findViewById(R.id.btnKorakNext);

        btnKorakGuess.setOnClickListener(v -> checkAnswer());


        startRound();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
    }

    private void startRound() {
        if (timer != null) timer.cancel();
        currentStep = 0;
        roundFinished = false;
       // btnKorakNext.setVisibility(View.GONE);
        resetStepFields();
        etKorakAnswer.setText("");
        tvKorakInfo.setText("Čekaj — otvara se novi korak svakih 10 sekundi.");
        updateHeader();
        updatePoints();
        openNextStep();
        startStepTimer();
    }

    private void resetStepFields() {
        tvStep1.setText("???"); tvStep2.setText("???"); tvStep3.setText("???");
        tvStep4.setText("???"); tvStep5.setText("???"); tvStep6.setText("???");
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
        timer = new CountDownTimer(70000, 1000) {
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
            Toast.makeText(requireContext(), "Unesi odgovor", Toast.LENGTH_SHORT).show();
            return;
        }
        if (guess.equalsIgnoreCase(answers[round - 1])) {
            int points = Math.max(20 - (currentStep - 1) * 2, 0);
            addPoints(points);
            tvKorakInfo.setText("Tačno! Osvajate " + points + " bodova.");
            finishRound();
        } else {
            Toast.makeText(requireContext(), "Netačno, pokušaj ponovo.", Toast.LENGTH_SHORT).show();
        }
        updateHeader();
    }

    private void updatePoints() {
        int points = Math.max(20 - currentStep * 2, 0);
        tvCurrentPoints.setText(String.valueOf(points));
    }

    private void addPoints(int points) {
        if (currentPlayer == 1) player1Score += points;
        else player2Score += points;
    }

    private void finishRound() {
        if (timer != null) timer.cancel();

        roundFinished = true;

        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvKorakInfo.setText("Runda završena! Sledeća za: " + seconds + "s");
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
        Toast.makeText(requireContext(), winner, Toast.LENGTH_LONG).show();
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_korak_to_mojbroj);
    }
}