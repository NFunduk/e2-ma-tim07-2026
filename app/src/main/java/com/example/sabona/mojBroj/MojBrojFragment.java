package com.example.sabona.mojBroj;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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

/**
 * Moj Broj fragment.
 *
 * Konzistentno s Ko Zna Zna:
 *  - AlertDialog za kreiranje/pridruživanje sesije
 *  - WAITING_P2 faza dok guest nije tu
 *  - layoutWaiting/layoutGame (isti pattern)
 *  - Prosleđuje sessionId sledećoj igri kroz Bundle args
 */
public class MojBrojFragment extends Fragment implements SensorEventListener {

    // ── Views ─────────────────────────────────────────────────────────
    private TextView   tvMojBrojRound, tvMojBrojTimer, tvMojBrojInfo;
    private TextView   tvTargetNumber, tvResult;
    private TextView   tvScore1, tvScore2;
    private TextView[] tvNums = new TextView[6];
    private Button     btnStop, btnStopNumbers, btnClear, btnBackspace, btnSubmit;
    private Button     btnPlus, btnMinus, btnMul, btnDiv, btnOpenP, btnCloseP;
    private EditText   etExpression;
    private View       layoutWaiting, layoutGame;
    private TextView   tvWaitingMsg;

    // ── UsedCount ─────────────────────────────────────────────────────
    private final int[] usedCount = new int[6];
    private final java.util.ArrayDeque<Integer> clickOrder = new java.util.ArrayDeque<>();

    // ── ViewModel & timeri ────────────────────────────────────────────
    private MojBrojViewModel viewModel;
    private CountDownTimer   roundTimer;
    private CountDownTimer   autoRevealTimer;
    private CountDownTimer   roundEndTimer;
    private boolean          roundTimerRunning = false;

    // ── Shake sensor ──────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor        accelerometer;
    private static final float SHAKE_THRESHOLD = 12f;
    private long  lastShakeTime = 0;
    private float gravX = 0f, gravY = 0f, gravZ = SensorManager.GRAVITY_EARTH;
    private static final float ALPHA = 0.8f;

    private boolean navigated = false;
    // ── Lifecycle ─────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_moj_broj, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupSensor();

        viewModel = new ViewModelProvider(this).get(MojBrojViewModel.class);
        observeViewModel();
        setupClickListeners();
        setupBackPressHandling();

        // Provjeri prosleđenu sesiju (iz Korak po korak)
        Bundle args = getArguments();
        if (args != null && args.containsKey("sessionId")) {
            String passedSessionId = args.getString("sessionId", "");
            boolean passedIsHost   = args.getBoolean("isHost", true);
            String passedHostUid   = args.getString("hostUid", "");
            String challengeId     = args.getString("challengeId", "");
            boolean isChallenge    = challengeId != null && !challengeId.isEmpty();

            if (!passedSessionId.isEmpty()) {
                if (isChallenge) {
                    GameSessionManager.get().setupAsSolo(passedSessionId);
                } else if (passedIsHost) {
                    GameSessionManager.get().setupAsHost(passedSessionId);
                } else {
                    GameSessionManager.get().setupAsGuest(passedSessionId, passedHostUid);
                }
                viewModel.init();
                return;
            }
        }

        // Nema prosleđene sesije — pokaži dialog (isti pattern kao KoZnaZna)
        //showJoinDialog();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelAllTimers();
    }

    // ── Join dialog ───────────────────────────────────────────────────

    /*private void showJoinDialog() {
        GameSessionManager mgr = GameSessionManager.get();
        String suggested = mgr.getMyUid().length() >= 6
                ? mgr.getMyUid().substring(0, 6).toUpperCase() : "MB001";

        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setTitle("Moj Broj");
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
        });

        b.setNegativeButton("Pridruži se (Igrač 2)", (d, w) -> {
            String sessionId = input.getText().toString().trim().toUpperCase();
            GameSessionManager.get().setupAsGuest(sessionId, "");
            viewModel.init();
        });

        b.setCancelable(false);
        b.show();
    }*/

    // ── Back press ────────────────────────────────────────────────────

    /**
     * Sprečava napuštanje igre pritiskom na sistemsko dugme "Nazad" dok
     * je partija u toku (sve faze osim GAME_OVER).
     */
    private void setupBackPressHandling() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        MojBrojViewModel.Phase currentPhase = viewModel.getPhase().getValue();
                        if (currentPhase == MojBrojViewModel.Phase.GAME_OVER) {
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

    // ── Shake sensor ──────────────────────────────────────────────────

    private void setupSensor() {
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        gravX = ALPHA * gravX + (1 - ALPHA) * event.values[0];
        gravY = ALPHA * gravY + (1 - ALPHA) * event.values[1];
        gravZ = ALPHA * gravZ + (1 - ALPHA) * event.values[2];
        float linX = event.values[0] - gravX;
        float linY = event.values[1] - gravY;
        float linZ = event.values[2] - gravZ;
        float magnitude = (float) Math.sqrt(linX * linX + linY * linY + linZ * linZ);
        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > 1200) {
                lastShakeTime = now;
                requireActivity().runOnUiThread(this::handleStop);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── Observeri ─────────────────────────────────────────────────────

    // ── STOP BUTTON FIX ───────────────────────────────────────────────────────────
// Replace observeViewModel() with this version.
// Key change: extract updatePhaseButtons() and observe isMyTurn independently
// so button states are never stale due to postValue ordering.

    private void observeViewModel() {

        viewModel.getRoundLabel().observe(getViewLifecycleOwner(),
                label -> tvMojBrojRound.setText(label));

        viewModel.getInfoText().observe(getViewLifecycleOwner(),
                text -> tvMojBrojInfo.setText(text));

        viewModel.getTargetNumber().observe(getViewLifecycleOwner(), num -> {
            if (num != null && num > 0) tvTargetNumber.setText(String.valueOf(num));
            else tvTargetNumber.setText("???");
        });

        viewModel.getOfferedNumbers().observe(getViewLifecycleOwner(), nums -> {
            if (nums == null) { for (TextView tv : tvNums) tv.setText("?"); return; }
            clickOrder.clear();
            for (int i = 0; i < 6; i++) {
                usedCount[i] = 0;
                tvNums[i].setText(String.valueOf(nums[i]));
                setNumUsed(i, false);
            }
        });

        viewModel.getLiveExprResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) {
                tvResult.setText("—");
                tvResult.setTextColor(getResources().getColor(R.color.dark_blue, null));
            } else {
                tvResult.setText(String.valueOf(result));
                Integer target = viewModel.getTargetNumber().getValue();
                tvResult.setTextColor(target != null && result.equals(target)
                        ? getResources().getColor(android.R.color.holo_green_dark, null)
                        : getResources().getColor(R.color.dark_blue, null));
            }
        });

        viewModel.getIDoneLocal().observe(getViewLifecycleOwner(), done -> {
            if (Boolean.TRUE.equals(done)) lockExpressionUI();
        });

        // Observe isMyTurn independently so button states are always up-to-date
        // even when the phase observer fires before isMyTurn's postValue is processed.
        viewModel.getIsMyTurn().observe(getViewLifecycleOwner(), myTurn -> {
            MojBrojViewModel.Phase currentPhase = viewModel.getPhase().getValue();
            if (currentPhase != null) {
                updatePhaseButtons(currentPhase, Boolean.TRUE.equals(myTurn));
            }
        });

        viewModel.getPhase().observe(getViewLifecycleOwner(), p -> {
            cancelAutoRevealTimer();
            boolean myTurn = Boolean.TRUE.equals(viewModel.getIsMyTurn().getValue());

            switch (p) {
                case LOADING:
                    showWaiting("Učitavanje...");
                    break;

                case WAITING_P2:
                    showWaiting("Čekam Igrača 2...\nKod: "
                            + GameSessionManager.get().getSessionId());
                    break;

                case IDLE:
                    hideWaiting();
                    roundTimerRunning = false;
                    resetExpressionUI();
                    updatePhaseButtons(p, myTurn);
                    etExpression.setEnabled(false);
                    setOperatorButtonsEnabled(false);
                    btnSubmit.setEnabled(false);
                    btnClear.setEnabled(false);
                    if (myTurn) startAutoRevealTimer(true);
                    break;

                case REVEAL_TARGET:
                    hideWaiting();
                    updatePhaseButtons(p, myTurn);
                    etExpression.setEnabled(false);
                    setOperatorButtonsEnabled(false);
                    btnSubmit.setEnabled(false);
                    btnClear.setEnabled(false);
                    if (myTurn) startAutoRevealTimer(false);
                    break;

                case PLAYING:
                    hideWaiting();
                    updatePhaseButtons(p, myTurn);
                    boolean alreadyDone = Boolean.TRUE.equals(viewModel.getIDoneLocal().getValue());
                    if (!alreadyDone) {
                        etExpression.setEnabled(true);
                        setOperatorButtonsEnabled(true);
                        btnSubmit.setEnabled(true);
                        btnClear.setEnabled(true);
                        if (!roundTimerRunning) { roundTimerRunning = true; startRoundTimer(); }
                    }
                    break;

                case ROUND_END:
                    roundTimerRunning = false;
                    cancelAllTimers();
                    lockExpressionUI();
                    tvMojBrojTimer.setText("00:00");
                    startRoundEndTimer();
                    break;

                case GAME_OVER:
                    roundTimerRunning = false;
                    cancelAllTimers();
                    lockExpressionUI();
                    break;
            }
        });

        viewModel.getFinalScores().observe(getViewLifecycleOwner(), scores -> {
            if (scores == null) return;
            // Čekamo matchResult pre navigacije
        });

        viewModel.getMatchResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null || navigated) return;
            navigated = true;

            String challengeId = getArguments() != null
                    ? getArguments().getString("challengeId", "") : "";

            Bundle args = new Bundle();

            if (!challengeId.isEmpty()) {
                // Izazov — idi na ChallengeResult sa KUMULATIVNIM skorom (svih 6 igara),
                // ne samo skorom iz Moj broj. result.myTotalScore čita totalScoreP1/P2
                // sa dokumenta sesije, koji se akumulira kroz sve igre.
                args.putString("challengeId", challengeId);
                args.putInt("myScore", result.myTotalScore);
                NavHostFragment.findNavController(MojBrojFragment.this)
                        .navigate(R.id.action_mojbroj_to_challengeResult, args);
            } else {
                // Normalna partija — postojeća logika
                args.putInt("player1Score", viewModel.getFinalScores().getValue() != null
                        ? viewModel.getFinalScores().getValue()[0] : 0);
                args.putInt("player2Score", viewModel.getFinalScores().getValue() != null
                        ? viewModel.getFinalScores().getValue()[1] : 0);
                args.putBoolean("friendly", result.friendly);
                args.putBoolean("won", result.won);
                args.putInt("starsDelta", result.starsDelta);
                args.putInt("tokensGained", result.tokensGained);
                args.putInt("myTotalScore", result.myTotalScore);
                args.putInt("opponentTotalScore", result.opponentTotalScore);
                NavHostFragment.findNavController(MojBrojFragment.this)
                        .navigate(R.id.action_mojbroj_to_gameover, args);
            }
        });

        viewModel.getLiveScores().observe(getViewLifecycleOwner(), scores -> {
            if (scores == null) return;
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateGameScore(scores[0], scores[1], null, null);
            }
        });
    }

    /**
     * Centralno mesto za enable/disable STOP dugmadi.
     * Poziva se i iz phase observera i iz isMyTurn observera —
     * tako se eliminiše race condition između ta dva postValue poziva.
     */
    private void updatePhaseButtons(MojBrojViewModel.Phase phase, boolean myTurn) {
        switch (phase) {
            case IDLE:
                btnStop.setEnabled(myTurn);        // aktivan samo za igrača čija je runda
                btnStopNumbers.setEnabled(false);
                break;
            case REVEAL_TARGET:
                btnStop.setEnabled(false);
                btnStopNumbers.setEnabled(myTurn); // aktivan samo za igrača čija je runda
                break;
            default:
                btnStop.setEnabled(false);
                btnStopNumbers.setEnabled(false);
                break;
        }
    }

    // ── Click listeneri ───────────────────────────────────────────────

    private void setupClickListeners() {
        btnStop.setOnClickListener(v -> handleStop());
        btnStopNumbers.setOnClickListener(v -> handleStop());

        btnClear.setOnClickListener(v -> {
            etExpression.setText("");
            tvResult.setText("—");
            for (int i = 0; i < 6; i++) { usedCount[i] = 0; setNumUsed(i, false); }
            clickOrder.clear();
        });

        btnBackspace.setOnClickListener(v -> handleBackspace());

        btnSubmit.setOnClickListener(v -> {
            String expr = etExpression.getText().toString().trim();
            int result = viewModel.submitExpression(expr);
            if (result == -1) {
                Toast.makeText(requireContext(), "Neispravan izraz.", Toast.LENGTH_SHORT).show();
            } else {
                cancelRoundTimer();
                roundTimerRunning = false;
            }
        });

        btnPlus.setOnClickListener(v   -> appendExpr(" + "));
        btnMinus.setOnClickListener(v  -> appendExpr(" - "));
        btnMul.setOnClickListener(v    -> appendExpr(" * "));
        btnDiv.setOnClickListener(v    -> appendExpr(" / "));
        btnOpenP.setOnClickListener(v  -> appendExpr("("));
        btnCloseP.setOnClickListener(v -> appendExpr(")"));

        for (int i = 0; i < 6; i++) {
            final int idx = i;
            tvNums[idx].setOnClickListener(v -> {
                MojBrojViewModel.Phase cur = viewModel.getPhase().getValue();
                if (cur != MojBrojViewModel.Phase.PLAYING) return;
                if (Boolean.TRUE.equals(viewModel.getIDoneLocal().getValue())) return;
                if (usedCount[idx] > 0) {
                    Toast.makeText(requireContext(), "Ovaj broj si već iskoristio!", Toast.LENGTH_SHORT).show();
                    return;
                }
                usedCount[idx]++;
                setNumUsed(idx, true);
                clickOrder.push(idx);
                appendExpr(tvNums[idx].getText().toString());
            });
        }

        etExpression.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { viewModel.onExpressionChanged(s.toString()); }
        });
    }

    // ── Tajmeri ───────────────────────────────────────────────────────

    private void startAutoRevealTimer(boolean forTarget) {
        cancelAutoRevealTimer();
        autoRevealTimer = new CountDownTimer(5_000, 1_000) {
            @Override
            public void onTick(long ms) {
                int sec = (int)(ms / 1000) + 1;
                tvMojBrojInfo.setText(forTarget
                        ? "Pritisni STOP ili šejkuj... (" + sec + ")"
                        : "Traženi: " + viewModel.getTargetNumber().getValue() + " — STOP za brojeve (" + sec + ")");
            }
            @Override
            public void onFinish() {
                if (forTarget) viewModel.onTargetAutoReveal();
                else           viewModel.onNumbersAutoReveal();
            }
        };
        autoRevealTimer.start();
    }

    private void startRoundTimer() {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(60_000, 1_000) {
            @Override
            public void onTick(long ms) {
                long s = ms / 1000;
                tvMojBrojTimer.setText(String.format("0%d:%02d", s / 60, s % 60));
            }
            @Override
            public void onFinish() {
                tvMojBrojTimer.setText("00:00");
                roundTimerRunning = false;
                viewModel.onRoundTimerFinished(etExpression.getText().toString().trim());
            }
        };
        roundTimer.start();
    }

    private void startRoundEndTimer() {
        cancelRoundEndTimer();
        roundEndTimer = new CountDownTimer(3_000, 1_000) {
            @Override public void onTick(long ms) { tvMojBrojTimer.setText(String.format("00:0%d", ms / 1000)); }
            @Override public void onFinish() { viewModel.onRoundEndCountdownFinished(); }
        };
        roundEndTimer.start();
    }

    private void cancelAutoRevealTimer() { if (autoRevealTimer != null) { autoRevealTimer.cancel(); autoRevealTimer = null; } }
    private void cancelRoundTimer()      { if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; } }
    private void cancelRoundEndTimer()   { if (roundEndTimer != null) { roundEndTimer.cancel(); roundEndTimer = null; } }
    private void cancelAllTimers()       { cancelAutoRevealTimer(); cancelRoundTimer(); cancelRoundEndTimer(); }

    // ── Helpers ───────────────────────────────────────────────────────

    private void handleStop() {
        MojBrojViewModel.Phase current = viewModel.getPhase().getValue();
        if (current != MojBrojViewModel.Phase.IDLE && current != MojBrojViewModel.Phase.REVEAL_TARGET) return;
        if (!Boolean.TRUE.equals(viewModel.getIsMyTurn().getValue())) return;
        cancelAutoRevealTimer();
        viewModel.onStop();
    }

    private void lockExpressionUI() {
        etExpression.setEnabled(false);
        setOperatorButtonsEnabled(false);
        btnSubmit.setEnabled(false);
        btnClear.setEnabled(false);
        btnStop.setEnabled(false);
        btnStopNumbers.setEnabled(false);
    }

    private void appendExpr(String token) {
        etExpression.setText(etExpression.getText().toString() + token);
        etExpression.setSelection(etExpression.getText().length());
    }

    private void setOperatorButtonsEnabled(boolean enabled) {
        btnPlus.setEnabled(enabled); btnMinus.setEnabled(enabled);
        btnMul.setEnabled(enabled);  btnDiv.setEnabled(enabled);
        btnOpenP.setEnabled(enabled); btnCloseP.setEnabled(enabled);
        btnBackspace.setEnabled(enabled);
    }

    // ── BACKSPACE FIX ─────────────────────────────────────────────────────────────

    private void handleBackspace() {
        String expr = etExpression.getText().toString();
        if (expr.isEmpty()) return;

        // Strip trailing whitespace (operators are appended as " + ", " - " etc.)
        String trimmed = expr.stripTrailing();
        if (trimmed.isEmpty()) {
            etExpression.setText("");
            return;
        }

        char lastChar = trimmed.charAt(trimmed.length() - 1);
        String newExpr;

        if (lastChar == '(' || lastChar == ')') {
            // Single parenthesis — remove it, no number tile to restore
            newExpr = trimmed.substring(0, trimmed.length() - 1);

        } else if (Character.isDigit(lastChar)) {
            // Walk backwards to find the full number run
            int start = trimmed.length() - 1;
            while (start > 0 && Character.isDigit(trimmed.charAt(start - 1))) {
                start--;
            }
            newExpr = trimmed.substring(0, start);

            // Restore the corresponding number tile
            if (!clickOrder.isEmpty()) {
                int idx = clickOrder.pop();
                if (usedCount[idx] > 0) {
                    usedCount[idx]--;
                    if (usedCount[idx] == 0) setNumUsed(idx, false);
                }
            }

        } else {
            // Operator token ("+", "-", "*", "/") — strip back past it and its leading space
            int lastSpace = trimmed.lastIndexOf(' ');
            newExpr = (lastSpace <= 0)
                    ? ""
                    : trimmed.substring(0, lastSpace).stripTrailing();
        }

        etExpression.setText(newExpr);
        etExpression.setSelection(newExpr.length());
    }

    private void setNumUsed(int idx, boolean used) {
        TextView tv = tvNums[idx];
        tv.setAlpha(used ? 0.35f : 1.0f);
        tv.setEnabled(!used);
        if (used) tv.setPaintFlags(tv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        else      tv.setPaintFlags(tv.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        tv.invalidate();
    }

    private void resetExpressionUI() {
        etExpression.setText("");
        tvResult.setText("—");
        tvTargetNumber.setText("???");
        tvMojBrojTimer.setText("01:00");
        clickOrder.clear();
        for (int i = 0; i < 6; i++) {
            tvNums[i].setText("?"); usedCount[i] = 0; setNumUsed(i, false);
        }
    }

    private void showWaiting(String msg) {
        if (layoutWaiting != null) { layoutWaiting.setVisibility(View.VISIBLE); if (tvWaitingMsg != null) tvWaitingMsg.setText(msg); }
        if (layoutGame != null) layoutGame.setVisibility(View.GONE);
    }

    private void hideWaiting() {
        if (layoutWaiting != null) layoutWaiting.setVisibility(View.GONE);
        if (layoutGame != null) layoutGame.setVisibility(View.VISIBLE);
    }

    private void bindViews(View v) {
        tvMojBrojRound  = v.findViewById(R.id.tvMojBrojRound);
        tvMojBrojTimer  = v.findViewById(R.id.tvMojBrojTimer);
        tvMojBrojInfo   = v.findViewById(R.id.tvMojBrojInfo);
        tvTargetNumber  = v.findViewById(R.id.tvTargetNumber);
        tvResult        = v.findViewById(R.id.tvResult);
        tvScore1        = v.findViewById(R.id.tvScore1);
        tvScore2        = v.findViewById(R.id.tvScore2);
        btnStop         = v.findViewById(R.id.btnStopTarget);
        btnStopNumbers  = v.findViewById(R.id.btnStopNumbers);
        btnClear        = v.findViewById(R.id.btnClearExpression);
        btnBackspace    = v.findViewById(R.id.btnBackspace);
        btnSubmit       = v.findViewById(R.id.btnSubmitExpression);
        btnPlus         = v.findViewById(R.id.btnOpPlus);
        btnMinus        = v.findViewById(R.id.btnOpMinus);
        btnMul          = v.findViewById(R.id.btnOpMultiply);
        btnDiv          = v.findViewById(R.id.btnOpDivide);
        btnOpenP        = v.findViewById(R.id.btnOpOpenParen);
        btnCloseP       = v.findViewById(R.id.btnOpCloseParen);
        etExpression    = v.findViewById(R.id.etExpression);
        layoutWaiting   = v.findViewById(R.id.layoutWaiting);
        layoutGame      = v.findViewById(R.id.layoutGame);
        tvWaitingMsg    = v.findViewById(R.id.tvWaitingMsg);

        tvNums[0] = v.findViewById(R.id.tvNum1);
        tvNums[1] = v.findViewById(R.id.tvNum2);
        tvNums[2] = v.findViewById(R.id.tvNum3);
        tvNums[3] = v.findViewById(R.id.tvNum4);
        tvNums[4] = v.findViewById(R.id.tvNum5);
        tvNums[5] = v.findViewById(R.id.tvNum6);
    }
}