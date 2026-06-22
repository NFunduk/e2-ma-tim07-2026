package com.example.sabona.skocko;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.MainActivity;
import com.example.sabona.R;
import com.example.sabona.repository.StatsRepository;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.lifecycle.ViewModelProvider;
import com.example.sabona.game.GameSessionManager;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

import java.util.Arrays;
import java.util.Random;

public class SkockoFragment extends Fragment {

    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo, currentGuess;
    private final TextView[] rows = new TextView[6];
    private final ImageButton[] symbolButtons = new ImageButton[6];
    private Button btnCheck, btnClear, btnNextSkocko;

    private final String[] symbols = {"☻", "■", "●", "♥", "▲", "★"};
    private final String[] secret = new String[4];
    private String[] guess = new String[4];
    private boolean roundEndTimerStarted = false;

    private int guessIndex = 0;
    private int attempt = 0;
    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;
    private int player1AttemptGroup = 0; // 1=pokušaj 1-2, 2=pokušaj 3-4, 3=pokušaj 5-6, 0=nije pogodio

    private boolean opponentChance = false;
    private boolean roundFinished = false;

    private CountDownTimer timer;
    private CountDownTimer finishTimer;

    private SkockoViewModel viewModel;
    private boolean multiplayerMode = false;

    private boolean myTurnNow = false;
    private int lastRenderedRound = -1;
    private int lastRenderedRowsCount = -1;
    private boolean alreadyNavigated = false;

    private boolean lastOpponentChance = false;

    private boolean roundEndTransitionStarted = false;
    private ListenerRegistration abandonListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skocko, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        connectViews(view);
        setupClicks();

        Bundle args = getArguments();

        if (args != null && args.containsKey("sessionId")) {
            String passedSessionId = args.getString("sessionId", "");
            boolean passedIsHost = args.getBoolean("isHost", true);
            String passedHostUid = args.getString("hostUid", "");

            if (!passedSessionId.isEmpty()) {
                multiplayerMode = true;

                if (passedIsHost) {
                    GameSessionManager.get().setupAsHost(passedSessionId);
                } else {
                    GameSessionManager.get().setupAsGuest(passedSessionId, passedHostUid);
                }

                viewModel = new ViewModelProvider(this).get(SkockoViewModel.class);
                observeViewModel();
                viewModel.init();
                listenForOpponentLeave();
                return;
            }
        }

        // Nema sola — vrati se nazad
        if (isAdded()) {
            NavHostFragment.findNavController(this).navigateUp();
        }
        startRound();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (timer != null) timer.cancel();
        if (finishTimer != null) finishTimer.cancel();
        if (abandonListener != null) {
            abandonListener.remove();
            abandonListener = null;
        }
    }

    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            round = state.round;
            player1Score = state.player1Score;
            player2Score = state.player2Score;
            attempt = state.attempt;
            opponentChance = state.opponentChance;
            roundFinished = state.roundFinished;

            currentPlayer = GameSessionManager.ROLE_PLAYER1.equals(state.activePlayerRole) ? 1 : 2;

            String[] remoteSecret = state.secret.split(",");
            if (remoteSecret.length == 4) {
                for (int i = 0; i < 4; i++) {
                    secret[i] = remoteSecret[i];
                }
            }

            if (lastRenderedRound != round) {
                lastRenderedRound = round;
                resetLocalRoundUi();
                lastRenderedRowsCount = -1;
            }

            renderRemoteRows(state);

            if (state.opponentChance && !lastOpponentChance) {
                lastOpponentChance = true;
                clearGuess();
                startTimer(10000);
            }

            if (!state.opponentChance) {
                lastOpponentChance = false;
            }


            updateHeader();
        });

        viewModel.getPhase().observe(getViewLifecycleOwner(), phase -> {
            if (phase == null) return;

            if (phase == SkockoViewModel.Phase.WAITING_P2) {
                enableGame(false);
                tvInfo.setText("Čekamo drugog igrača da se pridruži...");
            }


            if (phase == SkockoViewModel.Phase.ROUND_END) {
                enableGame(false);
                currentGuess.setText(buildSolutionText());
                startRoundEndTransition();
            }

            if (phase == SkockoViewModel.Phase.GAME_OVER) {
                showEndGame();
            }
        });

        viewModel.getMyTurn().observe(getViewLifecycleOwner(), isMyTurn -> {
            if (isMyTurn == null) return;

            myTurnNow = isMyTurn;

            if (multiplayerMode) {
                enableGame(myTurnNow && !roundFinished);

                if (myTurnNow) {
                    tvInfo.setText("Ti si na potezu.");
                } else {
                    tvInfo.setText("Čekaj, drugi igrač je na potezu.");
                }
            }
        });
    }

    private void resetLocalRoundUi() {
        roundEndTransitionStarted = false;
        roundEndTimerStarted = false;
        if (timer != null) timer.cancel();
        if (finishTimer != null) finishTimer.cancel();

        guessIndex = 0;
        guess = new String[4];

        for (TextView row : rows) {
            row.setText("_  _  _  _     |     ○ ○ ○ ○");
            row.setTextSize(18);
        }

        currentGuess.setText("_  _  _  _");
        btnNextSkocko.setVisibility(View.GONE);

        updateHeader();
        startTimer(30000);
    }

    private void connectViews(View view) {
        tvRound = view.findViewById(R.id.tvSkockoRound);
        tvPlayer = view.findViewById(R.id.tvSkockoPlayer);
        tvTimer = view.findViewById(R.id.tvSkockoTimer);
        tvScore = view.findViewById(R.id.tvSkockoScore);
        tvInfo = view.findViewById(R.id.tvSkockoInfo);
        currentGuess = view.findViewById(R.id.currentGuess);

        rows[0] = view.findViewById(R.id.row1);
        rows[1] = view.findViewById(R.id.row2);
        rows[2] = view.findViewById(R.id.row3);
        rows[3] = view.findViewById(R.id.row4);
        rows[4] = view.findViewById(R.id.row5);
        rows[5] = view.findViewById(R.id.row6);

        symbolButtons[0] = view.findViewById(R.id.symbol1);
        symbolButtons[1] = view.findViewById(R.id.symbol2);
        symbolButtons[2] = view.findViewById(R.id.symbol3);
        symbolButtons[3] = view.findViewById(R.id.symbol4);
        symbolButtons[4] = view.findViewById(R.id.symbol5);
        symbolButtons[5] = view.findViewById(R.id.symbol6);

        btnCheck = view.findViewById(R.id.btnCheck);
        btnClear = view.findViewById(R.id.btnClear);
        btnNextSkocko = view.findViewById(R.id.btnNextSkocko);
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
        if (timer != null) timer.cancel();
        if (finishTimer != null) finishTimer.cancel();

        if (!multiplayerMode) {
            generateSecret();
        }

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
        if (roundFinished) return;

        if (guessIndex >= 4) {
            Toast.makeText(requireContext(), "Već su izabrana 4 znaka.", Toast.LENGTH_SHORT).show();
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
        if (multiplayerMode && !myTurnNow) return;
        if (roundFinished) return;

        if (guessIndex < 4) {
            Toast.makeText(requireContext(), "Izaberi 4 znaka.", Toast.LENGTH_SHORT).show();
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

        if (multiplayerMode && viewModel != null) {
            viewModel.saveAttempt(
                    guessToString(guess),
                    correctPlace,
                    correctSymbol,
                    attempt + 1
            );
        }




        if (correctPlace == 4) {
            int points = calculatePoints(attempt + 1);
            // Zapamti u kom pokušaju je igrač 1 pogodio (za statistiku)
            if (currentPlayer == 1) {
                int att = attempt + 1;
                if (att <= 2) player1AttemptGroup = 1;
                else if (att <= 4) player1AttemptGroup = 2;
                else player1AttemptGroup = 3;
            }
            addPoints(points);
            tvInfo.setText("Tačno! Igrač " + currentPlayer + " osvaja " + points + " bodova.");
            finishRound();
            return;
        }

        attempt++;
        clearGuess();

        if (attempt == 6) {
            if (multiplayerMode && viewModel != null) {
                viewModel.startOpponentChance();
            } else {
                startOpponentChance();
            }
        } else {
            tvInfo.setText("Pokušaj " + (attempt + 1) + "/6.");
        }

        updateHeader();
    }

    private String guessToString(String[] values) {
        return values[0] + "," + values[1] + "," + values[2] + "," + values[3];
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
        if (timer != null) timer.cancel();

        opponentChance = true;

        if (multiplayerMode && viewModel != null) {
            viewModel.startOpponentChance();
        } else {
            switchPlayer();
        }

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
            Drawable drawable = ContextCompat.getDrawable(requireContext(), getIconForSymbol(guess[i]));

            if (drawable != null) {
                drawable.setBounds(0, 0, 58, 58);

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
                color = Color.RED;
            } else if (i < correctPlace + correctSymbol) {
                color = Color.YELLOW;
            } else {
                color = Color.rgb(7, 28, 95);
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

        if (multiplayerMode && viewModel != null) {
            viewModel.addPointsToActivePlayer(points);
        }
    }

    private void finishRound() {
        if (roundEndTimerStarted) return;
        roundEndTimerStarted = true;

        if (timer != null) timer.cancel();

        roundFinished = true;
        enableGame(false);
        currentGuess.setText(buildSolutionText());

        if (multiplayerMode && viewModel != null) {
            viewModel.finishRound();
            return;
        }

        finishTimer = new CountDownTimer(5000, 1000) {
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
        };

        finishTimer.start();
        updateHeader();
    }
    private void startRoundEndTransition() {
        if (roundEndTransitionStarted) return;
        roundEndTransitionStarted = true;

        if (timer != null) timer.cancel();
        if (finishTimer != null) finishTimer.cancel();

        finishTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvInfo.setText("Runda završena! Sledeća za: " + seconds + "s");
            }

            @Override
            public void onFinish() {
                if (multiplayerMode && viewModel != null) {
                    if (viewModel.amIAuthoritative()) {
                        viewModel.startNextRound();
                    }
                }
            }
        };

        finishTimer.start();
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
            Drawable drawable = ContextCompat.getDrawable(requireContext(), getIconForSymbol(secret[i]));

            if (drawable != null) {
                drawable.setBounds(0, 0, 58, 58);

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
        if (timer != null) timer.cancel();

        timer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                tvTimer.setText("00:" + String.format("%02d", seconds));
            }

            @Override
            public void onFinish() {

                if (multiplayerMode && !myTurnNow) {
                    return;
                }

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

    private String secretText() {
        return Arrays.toString(secret)
                .replace("[", "")
                .replace("]", "")
                .replace(",", " ");
    }

    private void switchPlayer() {
        currentPlayer = currentPlayer == 1 ? 2 : 1;

        if (multiplayerMode && viewModel != null) {
            viewModel.switchPlayer();
        }
    }

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        tvPlayer.setText("Na potezu: igrač " + currentPlayer);
        tvScore.setText(player1Score + " : " + player2Score);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateGameScore(player1Score, player2Score, null, null);
        }
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

                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Protivnik je napustio partiju.", Toast.LENGTH_SHORT).show();

                        if (!roundFinished) {
                            // Preskoči timer i završi rundu odmah
                            if (timer != null) timer.cancel();
                            if (opponentChance) {
                                // Protivnik je otišao tokom svog bonus pokušaja — završi rundu
                                finishRound();
                            } else {
                                // Protivnik je otišao tokom svoje glavne runde — preskoči na opponent chance ili završi
                                startOpponentChance();
                            }
                        }
                    });
                });
    }

    private void showEndGame() {

        if (alreadyNavigated) return;
        alreadyNavigated = true;
        String winner;
        if (player1Score > player2Score) {
            winner = "Pobednik igre Skočko je igrač 1!";
        } else if (player2Score > player1Score) {
            winner = "Pobednik igre Skočko je igrač 2!";
        } else {
            winner = "Skočko je nerešen!";
        }
        Toast.makeText(requireContext(), winner + " Sledi Korak po korak!", Toast.LENGTH_LONG).show();

        // Snimi statistiku za igrača 1 (player1Score i koji pokušaj)
        if (multiplayerMode) {
            new com.example.sabona.game.GameSessionRepository().isFriendlyMatch((friendly, e) -> {
                if (!Boolean.TRUE.equals(friendly)) {
                    new StatsRepository().saveSkockoResult(player1Score, player1AttemptGroup);
                }
            });
        } else {
            new StatsRepository().saveSkockoResult(player1Score, player1AttemptGroup);
        }

        Bundle args = new Bundle();

        if (multiplayerMode) {
            args.putString("sessionId", GameSessionManager.get().getSessionId());
            args.putBoolean("isHost", GameSessionManager.get().isPlayer1());
            args.putString("hostUid", GameSessionManager.get().getPlayer1Uid());
        }

        args.putString("challengeId", getArguments() != null ? getArguments().getString("challengeId", "") : "");
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_skocko_to_korak, args);
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

            Drawable drawable = ContextCompat.getDrawable(requireContext(), getIconForSymbol(values[i]));

            if (drawable != null) {
                drawable.setBounds(0, 0, 60, 60);

                spannable.setSpan(
                        new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                        start,
                        start + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        return spannable;
    }

    private void renderRemoteRows(SkockoGameState state) {
        if (state.rows == null) return;
        if (lastRenderedRowsCount == state.rows.size()) return;

        lastRenderedRowsCount = state.rows.size();

        for (int i = 0; i < rows.length; i++) {
            rows[i].setText("_  _  _  _     |     ○ ○ ○ ○");
            rows[i].setTextSize(18);
        }

        for (int i = 0; i < state.rows.size() && i < rows.length; i++) {
            String rowData = state.rows.get(i);
            String[] parts = rowData.split("\\|");

            if (parts.length != 3) continue;

            String[] rowGuess = parts[0].split(",");
            int correctPlace = Integer.parseInt(parts[1]);
            int correctSymbol = Integer.parseInt(parts[2]);

            setAttemptRowWithGuess(rows[i], rowGuess, correctPlace, correctSymbol);
        }
    }

    private void setAttemptRowWithGuess(TextView row, String[] rowGuess, int correctPlace, int correctSymbol) {
        String[] oldGuess = guess;
        guess = rowGuess;
        setAttemptRow(row, correctPlace, correctSymbol);
        guess = oldGuess;
    }

    private void saveSkockoResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("game", "skocko");
        result.put("player1Score", player1Score);
        result.put("player2Score", player2Score);
        result.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("gameResults")
                .add(result);
    }


}