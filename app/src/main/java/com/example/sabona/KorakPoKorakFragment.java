package com.example.sabona;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.repository.StatsRepository;
import com.example.sabona.viewModel.KorakViewModel;

public class KorakPoKorakFragment extends Fragment {

    private TextView   tvKorakRound, tvKorakTimer, tvKorakInfo, tvCurrentPoints;
    private TextView[] tvSteps = new TextView[7];
    private EditText   etKorakAnswer;
    private Button     btnKorakGuess;
    private ProgressBar progressBar;

    private KorakViewModel viewModel;
    private CountDownTimer activeTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_korak_po_korak, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);

        viewModel = new ViewModelProvider(this).get(KorakViewModel.class);
        observeViewModel();

        btnKorakGuess.setOnClickListener(v -> {
            String guess = etKorakAnswer.getText().toString().trim();
            if (guess.isEmpty()) {
                Toast.makeText(requireContext(), "Unesi odgovor", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean correct = viewModel.submitAnswer(guess);
            if (!correct) {
                Toast.makeText(requireContext(), "Netačno, pokušaj ponovo.", Toast.LENGTH_SHORT).show();
            }
            etKorakAnswer.setText("");
        });

        viewModel.init();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }

    private void observeViewModel() {

        viewModel.getPhase().observe(getViewLifecycleOwner(), phase -> {
            switch (phase) {

                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    btnKorakGuess.setEnabled(false);
                    break;

                case WAITING:
                    progressBar.setVisibility(View.GONE);
                    tvKorakInfo.setText("Čekanje na protivnika...");
                    btnKorakGuess.setEnabled(false);
                    break;

                case MAIN:
                    progressBar.setVisibility(View.GONE);
                    etKorakAnswer.setText("");
                    tvKorakRound.setText("Runda " + viewModel.getRound()
                            + "/2  —  Igrač " + viewModel.getActivePlayerNumber());
                    cancelTimer();
                    // Tajmer pokreću OBA telefona lokalno — sinhronizacija je preko Firestorea
                    startMainTimer();
                    break;

                case BONUS:
                    cancelTimer();
                    etKorakAnswer.setText("");
                    startBonusTimer();
                    break;

                case ROUND_END:
                    cancelTimer();
                    etKorakAnswer.setEnabled(false);
                    btnKorakGuess.setEnabled(false);
                    startRoundEndCountdown();
                    break;

                case GAME_OVER:
                    cancelTimer();
                    break;
            }
        });

        // Omogući/onemogući input zavisno od toga čiji je red
        viewModel.getIsMyTurn().observe(getViewLifecycleOwner(), myTurn -> {
            KorakViewModel.Phase currentPhase = viewModel.getPhase().getValue();
            if (currentPhase == KorakViewModel.Phase.MAIN) {
                etKorakAnswer.setEnabled(myTurn);
                btnKorakGuess.setEnabled(myTurn);
            } else if (currentPhase == KorakViewModel.Phase.BONUS) {
                // U bonus fazi, suprotni igrač odgovara
                etKorakAnswer.setEnabled(!myTurn);
                btnKorakGuess.setEnabled(!myTurn);
            }
        });

        viewModel.getInfoText().observe(getViewLifecycleOwner(),
                text -> tvKorakInfo.setText(text));

        viewModel.getCurrentPoints().observe(getViewLifecycleOwner(),
                pts -> tvCurrentPoints.setText(String.valueOf(pts)));

        viewModel.getStepsRevealed().observe(getViewLifecycleOwner(), count -> {
            if (viewModel.currentGame() == null) return;
            for (int i = 0; i < 7; i++) {
                if (i < count && i < viewModel.currentGame().steps.size()) {
                    tvSteps[i].setText(viewModel.currentGame().steps.get(i));
                } else {
                    tvSteps[i].setText("???");
                }
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), hasError -> {
            if (Boolean.TRUE.equals(hasError)) {
                Toast.makeText(requireContext(),
                        "Greška pri učitavanju!", Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getFinalScores().observe(getViewLifecycleOwner(), scores -> {
            if (scores == null) return;

            // Snimi statistiku za igrača 1
            int myScore = scores[0];
            int myStep = viewModel.getPlayer1GuessedAtStep();
            new StatsRepository().saveKorakResult(myScore, myStep);

            Bundle args = new Bundle();
            args.putInt("player1Score", scores[0]);
            args.putInt("player2Score", scores[1]);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_korak_to_mojbroj, args);
        });
    }

    // ── Tajmeri ───────────────────────────────────────────────────────

    private void startMainTimer() {
        activeTimer = new CountDownTimer(70_000, 1_000) {
            @Override
            public void onTick(long ms) {
                long secondsLeft = ms / 1000;
                tvKorakTimer.setText(String.format("00:%02d", secondsLeft));
                // Korak se otkriva svakih 10s — samo aktivni igrač piše u Firestore
                if (secondsLeft % 10 == 0 && secondsLeft != 70 && secondsLeft > 0) {
                    viewModel.revealNextStep();
                }
            }

            @Override
            public void onFinish() {
                tvKorakTimer.setText("00:00");
                viewModel.onMainTimerFinished();
            }
        };
        activeTimer.start();
    }

    private void startBonusTimer() {
        activeTimer = new CountDownTimer(10_000, 1_000) {
            @Override
            public void onTick(long ms) {
                tvKorakTimer.setText(String.format("00:%02d", ms / 1000));
            }

            @Override
            public void onFinish() {
                tvKorakTimer.setText("00:00");
                viewModel.onBonusTimerFinished();
            }
        };
        activeTimer.start();
    }

    private void startRoundEndCountdown() {
        activeTimer = new CountDownTimer(3_000, 1_000) {
            @Override
            public void onTick(long ms) {
                tvKorakTimer.setText(String.format("00:%02d", ms / 1000));
            }

            @Override
            public void onFinish() {
                // Samo player1 radi update u VM, ali oba pokreću countdown lokalno
                etKorakAnswer.setEnabled(true);
                btnKorakGuess.setEnabled(true);
                viewModel.onRoundEndCountdownFinished();
            }
        };
        activeTimer.start();
    }

    private void cancelTimer() {
        if (activeTimer != null) {
            activeTimer.cancel();
            activeTimer = null;
        }
    }

    private void bindViews(View view) {
        tvKorakRound    = view.findViewById(R.id.tvKorakRound);
        tvKorakTimer    = view.findViewById(R.id.tvKorakTimer);
        tvKorakInfo     = view.findViewById(R.id.tvKorakInfo);
        tvCurrentPoints = view.findViewById(R.id.tvCurrentPoints);
        progressBar     = view.findViewById(R.id.progressBarKorak);

        tvSteps[0] = view.findViewById(R.id.tvStep1);
        tvSteps[1] = view.findViewById(R.id.tvStep2);
        tvSteps[2] = view.findViewById(R.id.tvStep3);
        tvSteps[3] = view.findViewById(R.id.tvStep4);
        tvSteps[4] = view.findViewById(R.id.tvStep5);
        tvSteps[5] = view.findViewById(R.id.tvStep6);
        tvSteps[6] = view.findViewById(R.id.tvStep7);

        etKorakAnswer = view.findViewById(R.id.etKorakAnswer);
        btnKorakGuess = view.findViewById(R.id.btnKorakGuess);
    }
}