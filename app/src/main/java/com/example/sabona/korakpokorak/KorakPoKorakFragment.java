package com.example.sabona.korakpokorak;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.MainActivity;
import com.example.sabona.R;
import com.example.sabona.game.GameSessionManager;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Korak po Korak fragment.
 *
 * Konzistentno s Ko Zna Zna:
 *  - AlertDialog za kreiranje/pridruživanje sesije (isti pattern)
 *  - Host čeka u WAITING_P2, guest menja fazu u MAIN pri pridruživanju
 *  - ViewModel/LiveData arhitektura (tvoja, bolja)
 */
public class KorakPoKorakFragment extends Fragment {

    // ── Views ─────────────────────────────────────────────────────────
    private TextView   tvKorakRound, tvKorakTimer, tvKorakInfo, tvCurrentPoints;
    private TextView   tvScore1, tvScore2;
    private TextView[] tvSteps = new TextView[7];
    private EditText   etKorakAnswer;
    private Button     btnKorakGuess;
    private ProgressBar progressBar;
    private View       layoutWaiting, layoutGame;
    private TextView   tvWaitingMsg;

    private KorakViewModel viewModel;
    private CountDownTimer activeTimer;
    private int mainTimerStartedForRound = -1;

    private ListenerRegistration abandonListener;

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
        setupClickListeners();
        setupBackPressHandling();

        // Provjeri da li je sesija već postavljena (stigli smo iz KoZnaZna ili Spojnice)
        Bundle args = getArguments();
        if (args != null && args.containsKey("sessionId")) {
            String passedSessionId = args.getString("sessionId", "");
            boolean passedIsHost   = args.getBoolean("isHost", true);
            String passedHostUid   = args.getString("hostUid", "");

            if (!passedSessionId.isEmpty()) {
                if (passedIsHost) {
                    GameSessionManager.get().setupAsHost(passedSessionId);
                } else {
                    GameSessionManager.get().setupAsGuest(passedSessionId, passedHostUid);
                }
                viewModel.init();
                listenForOpponentLeave();
                return;
            }
        }

        // Nema prosleđene sesije — pokaži dialog (isti kao KoZnaZna)
        //showJoinDialog();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
        if (abandonListener != null) {
            abandonListener.remove();
            abandonListener = null;
        }
    }

    // ── Join dialog (isti pattern kao KoZnaZna) ───────────────────────

    /*private void showJoinDialog() {
        GameSessionManager mgr = GameSessionManager.get();
        String suggested = mgr.getMyUid().length() >= 6
                ? mgr.getMyUid().substring(0, 6).toUpperCase() : "KPK01";

        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setTitle("Korak po Korak");
        b.setMessage("Odaberi način igranja.\nKod sesije: " + suggested);

        EditText input = new EditText(requireContext());
        input.setHint("Kod sesije");
        input.setText(suggested);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        b.setView(input);

        b.setPositiveButton("Kreiraj (Igrač 1)", (d, w) -> {
            String sessionId = input.getText().toString().trim().toUpperCase();
            GameSessionManager.get().setupAsHost(sessionId);
            viewModel.init();
            listenForOpponentLeave();
        });

        b.setNegativeButton("Pridruži se (Igrač 2)", (d, w) -> {
            String sessionId = input.getText().toString().trim().toUpperCase();
            // hostUid se čita iz Firestore u ViewModel-u (setupAsGuest se zove tamo)
            GameSessionManager.get().setupAsGuest(sessionId, ""); // hostUid popuniti iz Firestore
            viewModel.init();
            listenForOpponentLeave();
        });

        b.setCancelable(false);
        b.show();
    }*/

    // ── Observeri ─────────────────────────────────────────────────────

    private void observeViewModel() {

        viewModel.getPhase().observe(getViewLifecycleOwner(), p -> {
            switch (p) {
                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    showWaiting("Učitavanje...");
                    btnKorakGuess.setEnabled(false);
                    break;

                case WAITING_P2:
                    progressBar.setVisibility(View.GONE);
                    showWaiting("Čekam Igrača 2...\nKod: "
                            + GameSessionManager.get().getSessionId());
                    btnKorakGuess.setEnabled(false);
                    break;

                case MAIN:
                    progressBar.setVisibility(View.GONE);
                    hideWaiting();
                    tvKorakRound.setText("Runda " + viewModel.getRound()
                            + "/2  —  Igrač " + viewModel.getActivePlayerNumber());

                    boolean myTurnMain = Boolean.TRUE.equals(viewModel.getIsMyTurn().getValue());
                    etKorakAnswer.setEnabled(myTurnMain);
                    btnKorakGuess.setEnabled(myTurnMain);

                    // Tajmer se pokreće samo JEDNOM po rundi, ne na svaki otkriveni korak
                    if (mainTimerStartedForRound != viewModel.getRound()) {
                        mainTimerStartedForRound = viewModel.getRound();
                        etKorakAnswer.setText("");
                        cancelTimer();
                        startMainTimer();
                    }
                    break;

                case BONUS:
                    hideWaiting();
                    cancelTimer();
                    etKorakAnswer.setText("");

                    boolean myTurnBonus = Boolean.TRUE.equals(viewModel.getIsMyTurn().getValue());
                    // U bonus fazi odgovara PROTIVNIK (suprotno od activePlayer)
                    etKorakAnswer.setEnabled(!myTurnBonus);
                    btnKorakGuess.setEnabled(!myTurnBonus);

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

        viewModel.getIsMyTurn().observe(getViewLifecycleOwner(), myTurn -> {
            KorakViewModel.Phase currentPhase = viewModel.getPhase().getValue();
            if (currentPhase == KorakViewModel.Phase.MAIN) {
                etKorakAnswer.setEnabled(myTurn);
                btnKorakGuess.setEnabled(myTurn);
            } else if (currentPhase == KorakViewModel.Phase.BONUS) {
                // U bonus fazi odgovara PROTIVNIK (suprotno od activePlayer)
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
            Bundle args = new Bundle();
            args.putInt("player1Score", scores[0]);
            args.putInt("player2Score", scores[1]);
            args.putString("sessionId", GameSessionManager.get().getSessionId());
            args.putBoolean("isHost", GameSessionManager.get().isPlayer1());
            args.putString("hostUid", GameSessionManager.get().getPlayer1Uid());
            try {
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_korak_to_mojbroj, args);
            } catch (Exception e) {
                // Navigation fallback
            }
        });

        viewModel.getLiveScores().observe(getViewLifecycleOwner(), scores -> {
            if (scores == null) return;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateGameScore(scores[0], scores[1], null, null);
            }
        });
    }

    // ── Click listeneri ───────────────────────────────────────────────

    private void setupClickListeners() {
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
    }

    // ── Tajmeri ───────────────────────────────────────────────────────

    private void startMainTimer() {
        activeTimer = new CountDownTimer(70_000, 1_000) {
            @Override
            public void onTick(long ms) {
                if (!isAdded()) return;
                long secondsLeft = ms / 1000;
                tvKorakTimer.setText(String.format("00:%02d", secondsLeft));
                if (secondsLeft % 10 == 0 && secondsLeft != 70 && secondsLeft > 0) {
                    viewModel.revealNextStep();
                }
            }
            @Override
            public void onFinish() {
                if (!isAdded()) return;
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
                if (!isAdded()) return;
                tvKorakTimer.setText(String.format("00:%02d", ms / 1000));
            }
            @Override
            public void onFinish() {
                if (!isAdded()) return;
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
                if (!isAdded()) return;
                tvKorakTimer.setText(String.format("00:0%d", ms / 1000));
            }
            @Override
            public void onFinish() {
                if (!isAdded()) return;
                viewModel.onRoundEndCountdownFinished();
            }
        };
        activeTimer.start();
    }

    private void cancelTimer() {
        if (activeTimer != null) { activeTimer.cancel(); activeTimer = null; }
    }

    // ── Back press ────────────────────────────────────────────────────

    /**
     * Sprečava napuštanje igre pritiskom na sistemsko dugme "Nazad" dok
     * je partija u toku (sve faze osim GAME_OVER). Pre toga je dugme
     * "Nazad" vraćalo igrača na ekran Spojnice usred igre.
     */
    private void setupBackPressHandling() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        KorakViewModel.Phase currentPhase = viewModel.getPhase().getValue();
                        if (currentPhase == KorakViewModel.Phase.GAME_OVER) {
                            // Dozvoli izlazak kada je partija gotova
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                            return;
                        }
                        Toast.makeText(requireContext(),
                                "Ne možeš napustiti igru dok partija traje.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void listenForOpponentLeave() {
        String sessionId = GameSessionManager.get().getSessionId();
        if (sessionId == null || sessionId.isEmpty()) return;

        abandonListener = new com.example.sabona.game.GameSessionRepository()
                .listenRootSession((snap, e) -> {
                    if (snap == null || !snap.exists()) return;
                    String leftUid = snap.getString("leftByUid");
                    if (leftUid == null) return;

                    String myUid = com.google.firebase.auth.FirebaseAuth.getInstance()
                            .getCurrentUser() != null
                            ? com.google.firebase.auth.FirebaseAuth.getInstance()
                            .getCurrentUser().getUid()
                            : null;
                    if (myUid == null || leftUid.equals(myUid)) return;

                    // Protivnik je otišao
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Protivnik je napustio partiju.", Toast.LENGTH_SHORT).show();

                        KorakViewModel.Phase currentPhase = viewModel.getPhase().getValue();
                        if (currentPhase == KorakViewModel.Phase.MAIN) {
                            cancelTimer();
                            viewModel.onMainTimerFinished();
                        } else if (currentPhase == KorakViewModel.Phase.BONUS) {
                            cancelTimer();
                            viewModel.onBonusTimerFinished();
                        }
                    });
                });
    }

    private void showWaiting(String msg) {
        if (layoutWaiting != null) {
            layoutWaiting.setVisibility(View.VISIBLE);
            if (tvWaitingMsg != null) tvWaitingMsg.setText(msg);
        }
        if (layoutGame != null) layoutGame.setVisibility(View.GONE);
    }

    private void hideWaiting() {
        if (layoutWaiting != null) layoutWaiting.setVisibility(View.GONE);
        if (layoutGame != null) layoutGame.setVisibility(View.VISIBLE);
    }

    // ── Bind ──────────────────────────────────────────────────────────

    private void bindViews(View view) {
        tvKorakRound    = view.findViewById(R.id.tvKorakRound);
        tvKorakTimer    = view.findViewById(R.id.tvKorakTimer);
        tvKorakInfo     = view.findViewById(R.id.tvKorakInfo);
        tvCurrentPoints = view.findViewById(R.id.tvCurrentPoints);
        tvScore1        = view.findViewById(R.id.tvScore1);
        tvScore2        = view.findViewById(R.id.tvScore2);
        progressBar     = view.findViewById(R.id.progressBarKorak);
        layoutWaiting   = view.findViewById(R.id.layoutWaiting);
        layoutGame      = view.findViewById(R.id.layoutGame);
        tvWaitingMsg    = view.findViewById(R.id.tvWaitingMsg);

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