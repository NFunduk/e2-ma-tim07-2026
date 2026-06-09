package com.example.sabona.mojBroj;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.R;

public class MojBrojFragment extends Fragment implements SensorEventListener {

    // ── Views ─────────────────────────────────────────────────────────
    private TextView   tvMojBrojRound, tvMojBrojTimer, tvMojBrojInfo;
    private TextView   tvTargetNumber;
    private TextView[] tvNums = new TextView[6];
    private TextView   tvResult;
    private Button     btnStop, btnStopNumbers, btnClear, btnBackspace, btnSubmit;
    private Button     btnPlus, btnMinus, btnMul, btnDiv, btnOpenP, btnCloseP;
    private EditText   etExpression;

    // ── Praćenje iskorišćenih brojeva ─────────────────────────────────
    private final int[] usedCount = new int[6];

    // ── ViewModel & timeri ────────────────────────────────────────────
    private MojBrojViewModel viewModel;
    private CountDownTimer   roundTimer;
    private CountDownTimer   autoRevealTimer;
    private CountDownTimer   roundEndTimer;

    // ── Shake sensor ──────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor        accelerometer;
    private static final float SHAKE_THRESHOLD = 12f;
    private long lastShakeTime = 0;
    private float gravX = 0f, gravY = 0f, gravZ = SensorManager.GRAVITY_EARTH;
    private static final float ALPHA = 0.8f;

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

        viewModel.init();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
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

    // ── Shake sensor ──────────────────────────────────────────────────

    private void setupSensor() {
        sensorManager = (SensorManager) requireActivity()
                .getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
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

    // ── Observer ──────────────────────────────────────────────────────

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
            if (nums == null) {
                for (TextView tv : tvNums) tv.setText("?");
                return;
            }
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
                if (target != null && result == target) {
                    tvResult.setTextColor(
                            getResources().getColor(android.R.color.holo_green_dark, null));
                } else {
                    tvResult.setTextColor(getResources().getColor(R.color.dark_blue, null));
                }
            }
        });

        viewModel.getIsMyTurn().observe(getViewLifecycleOwner(), myTurn -> {
            // Refresh UI zavisno od faze i reda
            MojBrojViewModel.Phase currentPhase = viewModel.getPhase().getValue();
            if (currentPhase == MojBrojViewModel.Phase.IDLE
                    || currentPhase == MojBrojViewModel.Phase.REVEAL_TARGET) {
                btnStop.setEnabled(myTurn);
                btnStopNumbers.setEnabled(myTurn);
            }
            if (currentPhase == MojBrojViewModel.Phase.PLAYING) {
                etExpression.setEnabled(myTurn);
                setOperatorButtonsEnabled(myTurn);
                btnSubmit.setEnabled(myTurn);
                btnClear.setEnabled(myTurn);
            }
        });

        viewModel.getPhase().observe(getViewLifecycleOwner(), p -> {
            cancelAutoRevealTimer();

            boolean myTurn = Boolean.TRUE.equals(viewModel.getIsMyTurn().getValue());

            switch (p) {
                case IDLE:
                    resetExpressionUI();
                    btnStop.setEnabled(myTurn);
                    btnStopNumbers.setEnabled(false);
                    etExpression.setEnabled(false);
                    setOperatorButtonsEnabled(false);
                    btnSubmit.setEnabled(false);
                    btnClear.setEnabled(false);
                    if (myTurn) startAutoRevealTimer(true);
                    break;

                case REVEAL_TARGET:
                    btnStop.setEnabled(false);
                    btnStopNumbers.setEnabled(myTurn);
                    if (myTurn) startAutoRevealTimer(false);
                    break;

                case PLAYING:
                    btnStop.setEnabled(false);
                    btnStopNumbers.setEnabled(false);
                    etExpression.setEnabled(myTurn);
                    setOperatorButtonsEnabled(myTurn);
                    btnSubmit.setEnabled(myTurn);
                    btnClear.setEnabled(myTurn);
                    if (myTurn) startRoundTimer();
                    break;

                case ROUND_END:
                    cancelAllTimers();
                    etExpression.setEnabled(false);
                    setOperatorButtonsEnabled(false);
                    btnStop.setEnabled(false);
                    btnStopNumbers.setEnabled(false);
                    btnSubmit.setEnabled(false);
                    btnClear.setEnabled(false);
                    tvMojBrojTimer.setText("00:00");
                    startRoundEndTimer();
                    break;

                case GAME_OVER:
                    cancelAllTimers();
                    break;
            }
        });

        viewModel.getFinalScores().observe(getViewLifecycleOwner(), scores -> {
            if (scores == null) return;
            new CountDownTimer(3_000, 1_000) {
                @Override public void onTick(long ms) {
                    tvMojBrojTimer.setText(String.format("00:0%d", ms / 1000));
                }
                @Override public void onFinish() {
                    if (!isAdded()) return;
                    Bundle args = new Bundle();
                    args.putInt("player1Score", scores[0]);
                    args.putInt("player2Score", scores[1]);
                    NavHostFragment.findNavController(MojBrojFragment.this)
                            .navigate(R.id.action_mojbroj_to_gameover, args);
                }
            }.start();
        });
    }

    // ── Click listeneri ───────────────────────────────────────────────

    private void setupClickListeners() {
        btnStop.setOnClickListener(v -> handleStop());
        btnStopNumbers.setOnClickListener(v -> handleStop());

        btnClear.setOnClickListener(v -> {
            etExpression.setText("");
            tvResult.setText("—");
            for (int i = 0; i < 6; i++) {
                usedCount[i] = 0;
                setNumUsed(i, false);
            }
        });

        btnBackspace.setOnClickListener(v -> handleBackspace());

        btnSubmit.setOnClickListener(v -> {
            String expr = etExpression.getText().toString().trim();
            int result = viewModel.submitExpression(expr);
            if (result == -1) {
                Toast.makeText(requireContext(),
                        "Neispravan izraz.", Toast.LENGTH_SHORT).show();
            } else {
                cancelRoundTimer();
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
                if (viewModel.getPhase().getValue() != MojBrojViewModel.Phase.PLAYING) return;
                if (!Boolean.TRUE.equals(viewModel.getIsMyTurn().getValue())) return;
                if (usedCount[idx] > 0) {
                    Toast.makeText(requireContext(),
                            "Ovaj broj si već iskoristio!", Toast.LENGTH_SHORT).show();
                    return;
                }
                usedCount[idx]++;
                setNumUsed(idx, true);
                appendExpr(tvNums[idx].getText().toString());
            });
        }

        etExpression.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                viewModel.onExpressionChanged(s.toString());
            }
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
                        : "Traženi: " + viewModel.getTargetNumber().getValue()
                        + " — STOP za brojeve (" + sec + ")");
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
                String expr = etExpression.getText().toString().trim();
                viewModel.onRoundTimerFinished(expr);
            }
        };
        roundTimer.start();
    }

    private void startRoundEndTimer() {
        cancelRoundEndTimer();
        roundEndTimer = new CountDownTimer(3_000, 1_000) {
            @Override
            public void onTick(long ms) {
                tvMojBrojTimer.setText(String.format("00:0%d", ms / 1000));
            }
            @Override
            public void onFinish() {
                viewModel.onRoundEndCountdownFinished();
            }
        };
        roundEndTimer.start();
    }

    private void cancelAutoRevealTimer() {
        if (autoRevealTimer != null) { autoRevealTimer.cancel(); autoRevealTimer = null; }
    }
    private void cancelRoundTimer() {
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
    }
    private void cancelRoundEndTimer() {
        if (roundEndTimer != null) { roundEndTimer.cancel(); roundEndTimer = null; }
    }
    private void cancelAllTimers() {
        cancelAutoRevealTimer();
        cancelRoundTimer();
        cancelRoundEndTimer();
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void handleStop() {
        MojBrojViewModel.Phase current = viewModel.getPhase().getValue();
        if (current != MojBrojViewModel.Phase.IDLE
                && current != MojBrojViewModel.Phase.REVEAL_TARGET) return;
        if (!Boolean.TRUE.equals(viewModel.getIsMyTurn().getValue())) return;
        cancelAutoRevealTimer();
        viewModel.onStop();
    }

    private void appendExpr(String token) {
        etExpression.setText(etExpression.getText().toString() + token);
        etExpression.setSelection(etExpression.getText().length());
    }

    private void setOperatorButtonsEnabled(boolean enabled) {
        btnPlus.setEnabled(enabled);
        btnMinus.setEnabled(enabled);
        btnMul.setEnabled(enabled);
        btnDiv.setEnabled(enabled);
        btnOpenP.setEnabled(enabled);
        btnCloseP.setEnabled(enabled);
        btnBackspace.setEnabled(enabled);
    }

    private void handleBackspace() {
        String expr = etExpression.getText().toString();
        if (expr.isEmpty()) return;

        String trimmed = expr.stripTrailing();
        if (trimmed.isEmpty()) { etExpression.setText(""); return; }

        int lastSpace = trimmed.lastIndexOf(' ');
        String lastToken;
        String newExpr;
        if (lastSpace == -1) {
            lastToken = trimmed;
            newExpr = "";
        } else {
            lastToken = trimmed.substring(lastSpace + 1);
            newExpr = trimmed.substring(0, lastSpace);
        }

        int[] offered = viewModel.getOfferedNumbers().getValue();
        if (offered != null) {
            try {
                int tokenVal = Integer.parseInt(lastToken);
                for (int i = 0; i < 6; i++) {
                    if (usedCount[i] > 0 && offered[i] == tokenVal) {
                        usedCount[i]--;
                        if (usedCount[i] == 0) setNumUsed(i, false);
                        break;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        etExpression.setText(newExpr);
        etExpression.setSelection(newExpr.length());
    }

    private void setNumUsed(int idx, boolean used) {
        TextView tv = tvNums[idx];
        if (used) {
            tv.setAlpha(0.35f);
            tv.setEnabled(false);
            tv.setPaintFlags(tv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            tv.setAlpha(1.0f);
            tv.setEnabled(true);
            tv.setPaintFlags(tv.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
    }

    private void resetExpressionUI() {
        etExpression.setText("");
        tvResult.setText("—");
        tvTargetNumber.setText("???");
        for (int i = 0; i < 6; i++) {
            tvNums[i].setText("?");
            usedCount[i] = 0;
            setNumUsed(i, false);
        }
    }

    private void bindViews(View v) {
        tvMojBrojRound  = v.findViewById(R.id.tvMojBrojRound);
        tvMojBrojTimer  = v.findViewById(R.id.tvMojBrojTimer);
        tvMojBrojInfo   = v.findViewById(R.id.tvMojBrojInfo);
        tvTargetNumber  = v.findViewById(R.id.tvTargetNumber);
        tvResult        = v.findViewById(R.id.tvResult);
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

        tvNums[0] = v.findViewById(R.id.tvNum1);
        tvNums[1] = v.findViewById(R.id.tvNum2);
        tvNums[2] = v.findViewById(R.id.tvNum3);
        tvNums[3] = v.findViewById(R.id.tvNum4);
        tvNums[4] = v.findViewById(R.id.tvNum5);
        tvNums[5] = v.findViewById(R.id.tvNum6);
    }
}