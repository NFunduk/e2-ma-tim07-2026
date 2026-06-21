package com.example.sabona.asocijacije;

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

import com.example.sabona.MainActivity;
import com.example.sabona.R;
import com.example.sabona.model.AssociationGame;

import com.example.sabona.repository.StatsRepository;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import java.util.List;

import androidx.lifecycle.ViewModelProvider;

import com.example.sabona.game.GameSessionManager;

public class AssociationsFragment extends Fragment {

    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo;

    private boolean columnSolvedThisTurn = false;
    private Button[][] fieldButtons = new Button[4][4];
    private TextView[] columnSolutions = new TextView[4];
    private EditText[] columnInputs = new EditText[4];
    private Button[] guessColumnButtons = new Button[4];
    private EditText finalInput;
    private Button btnGuessFinal, btnPassTurn;

    private boolean roundEndTimerStarted = false;
    private long lastTimerEnd = 0L;

    private int round = 1;
    private int currentPlayer = 1;
    private int player1Score = 0;
    private int player2Score = 0;

    private String currentRemotePhase = "";

    private boolean[][] opened = new boolean[4][4];
    private boolean[] columnSolved = new boolean[4];
    private boolean finalSolved = false;
    private boolean fieldOpenedThisTurn = false;
    private boolean roundFinished = false;

    private CountDownTimer timer;

    private List<AssociationGame> games;

    private AsocijacijeViewModel viewModel;
    private boolean multiplayerMode = false;
    private boolean suppressRemoteUpdate = false;

    private boolean myTurnNow = false;
    private int lastRenderedRound = -1;
    private boolean alreadyNavigated = false;
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

                loadAssociationsForMultiplayer();
                return;
            }
        }

        // Nema sola — vrati se nazad
        if (isAdded()) {
            NavHostFragment.findNavController(this).navigateUp();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
    }

    private void loadAssociationsForMultiplayer() {
        new AssociationRepository().getAssociations(
                new AssociationRepository.Callback() {

                    @Override
                    public void onSuccess(List<AssociationGame> loadedGames) {
                        if (loadedGames == null || loadedGames.size() < 2) {
                            Toast.makeText(
                                    requireContext(),
                                    "U bazi moraju postojati bar 2 asocijacije.",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        games = loadedGames;

                        viewModel = new ViewModelProvider(AssociationsFragment.this)
                                .get(AsocijacijeViewModel.class);

                        observeViewModel();
                        viewModel.init(games);
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(
                                requireContext(),
                                "Greška pri učitavanju asocijacija",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || games == null) return;
            currentRemotePhase = state.phase;
            round = state.round;

            if (lastRenderedRound != round) {
                lastRenderedRound = round;
                resetRoundUiOnlyVisual();
            }

            if (state.phaseEndsAtMillis > 0 && state.phaseEndsAtMillis != lastTimerEnd) {
                lastTimerEnd = state.phaseEndsAtMillis;
                startTimer(state.phaseEndsAtMillis);
            }
            currentPlayer = GameSessionManager.ROLE_PLAYER1.equals(state.activePlayerRole) ? 1 : 2;
            player1Score = state.player1Score;
            player2Score = state.player2Score;

            finalSolved = state.finalSolved;
            fieldOpenedThisTurn = state.fieldOpenedThisTurn;
            roundFinished = state.roundFinished;

            applyRemoteGameOrder(state);
            applyRemoteOpenedFields(state);
            applyRemoteColumns(state);

            updateHeader();
        });

        viewModel.getPhase().observe(getViewLifecycleOwner(), phase -> {
            if (phase == null) return;

            if (phase == AsocijacijeViewModel.Phase.WAITING_P2) {
                tvInfo.setText("Čekamo drugog igrača da se pridruži...");
                setAllInputsEnabled(false);
            }



            if (phase == AsocijacijeViewModel.Phase.ROUND_END) {
                revealAllAnswers();
                setAllInputsEnabled(false);
                tvInfo.setText("Runda završena! Prikazana su rešenja.");
            }

            if (phase == AsocijacijeViewModel.Phase.GAME_OVER) {
                showEndGame();
            }
        });

        viewModel.getMyTurn().observe(getViewLifecycleOwner(), isMyTurn -> {
            if (isMyTurn == null) return;

            myTurnNow = isMyTurn;

            if (multiplayerMode) {
                refreshEnabledState();

                if (myTurnNow) {
                    tvInfo.setText("Ti si na potezu.");
                } else {
                    tvInfo.setText("Čekaj, drugi igrač je na potezu.");
                }
            }
        });
    }

    private void refreshEnabledState() {
        boolean canPlay = myTurnNow && !roundFinished && !finalSolved;

        for (int col = 0; col < 4; col++) {
            columnInputs[col].setEnabled(canPlay);
            guessColumnButtons[col].setEnabled(canPlay);

            for (int row = 0; row < 4; row++) {
                fieldButtons[col][row].setEnabled(
                        canPlay &&
                                !fieldOpenedThisTurn &&
                                !columnSolvedThisTurn &&
                                !opened[col][row] &&
                                !columnSolved[col]
                );
            }
        }

        finalInput.setEnabled(canPlay);
        btnGuessFinal.setEnabled(canPlay);
        btnPassTurn.setEnabled(canPlay && (fieldOpenedThisTurn || columnSolvedThisTurn));
    }

    private void resetRoundUiOnlyVisual() {
        if (timer != null) timer.cancel();

        finalSolved = false;
        fieldOpenedThisTurn = false;
        roundFinished = false;
        roundEndTimerStarted = false;
        columnSolvedThisTurn = false;

        for (int col = 0; col < 4; col++) {
            columnSolved[col] = false;
            columnSolutions[col].setText("?");
            columnInputs[col].setText("");

            for (int row = 0; row < 4; row++) {
                opened[col][row] = false;
                fieldButtons[col][row].setText("?");
            }
        }

        finalInput.setText("");
        updateHeader();
       // startTimer();
    }

    private void applyRemoteGameOrder(AsocijacijeGameState state) {
        if (state.game0Id == null || state.game1Id == null) return;

        AssociationGame first = null;
        AssociationGame second = null;

        for (AssociationGame g : games) {
            if (state.game0Id.equals(g.docId)) first = g;
            if (state.game1Id.equals(g.docId)) second = g;
        }

        if (first != null && second != null) {
            List<AssociationGame> ordered = new java.util.ArrayList<>();
            ordered.add(first);
            ordered.add(second);
            games = ordered;
        }
    }

    private void applyRemoteOpenedFields(AsocijacijeGameState state) {
        if (state.opened == null || state.opened.size() < 16) return;

        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                int index = col * 4 + row;
                boolean isOpened = state.opened.get(index);

                opened[col][row] = isOpened;

                if (isOpened) {
                    fieldButtons[col][row].setText(
                            games.get(round - 1).columns.get(col).fields.get(row)
                    );
                } else {
                    fieldButtons[col][row].setText("?");
                }
            }
        }

        refreshEnabledState();
    }

    private void applyRemoteColumns(AsocijacijeGameState state) {
        if (state.columnSolved == null || state.columnSolved.size() < 4) return;

        for (int col = 0; col < 4; col++) {
            boolean solved = state.columnSolved.get(col);
            columnSolved[col] = solved;

            if (solved) {
                columnSolutions[col].setText(
                        games.get(round - 1).columns.get(col).answer
                );
            } else {
                columnSolutions[col].setText("?");
            }
        }

        if (state.finalSolved) {
            revealAllAnswers();
        }

        refreshEnabledState();
    }

    private void resetRoundUiFromState() {
        if (timer != null) timer.cancel();

        for (int col = 0; col < 4; col++) {
            columnInputs[col].setText("");
            finalInput.setText("");
        }

        updateHeader();
        startTimer();
    }

    private void setAllInputsEnabled(boolean enabled) {
        for (int col = 0; col < 4; col++) {
            columnInputs[col].setEnabled(enabled);
            guessColumnButtons[col].setEnabled(enabled);

            for (int row = 0; row < 4; row++) {
                fieldButtons[col][row].setEnabled(enabled);
            }
        }

        finalInput.setEnabled(enabled);
        btnGuessFinal.setEnabled(enabled);
        btnPassTurn.setEnabled(enabled);
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

        btnPassTurn = view.findViewById(R.id.btnPassTurn);
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

        btnPassTurn.setOnClickListener(v -> {
            if (!fieldOpenedThisTurn && !columnSolvedThisTurn) {
                Toast.makeText(requireContext(), "Prvo otvori polje ili pogodi kolonu.", Toast.LENGTH_SHORT).show();
                return;
            }

            passTurn();
        });
    }

    private void startRound() {
        if (timer != null) timer.cancel();

        finalSolved = false;
        fieldOpenedThisTurn = false;
        columnSolvedThisTurn = false;
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
        btnPassTurn.setEnabled(true);

        tvInfo.setText("Otvori jedno polje, pa pogodi kolonu ili konačno rešenje.");
        updateHeader();
        startTimer();
    }

    private void openField(int col, int row) {

        if (multiplayerMode && !myTurnNow) return;
        if (opened[col][row] || columnSolved[col] || finalSolved || fieldOpenedThisTurn || roundFinished) {
            return;
        }

        opened[col][row] = true;
        fieldOpenedThisTurn = true;

        if (multiplayerMode && viewModel != null) {
            viewModel.openField(col, row);
        }

        fieldButtons[col][row].setText(games.get(round - 1).columns.get(col).fields.get(row));
        disableFieldOpening();

        tvInfo.setText("Otvoreno polje. Sada možeš da pogađaš.");
        updateHeader();
    }

    private void passTurn() {
        if (multiplayerMode && viewModel != null) {
            columnSolvedThisTurn = false;
            fieldOpenedThisTurn = false;
            viewModel.passTurn();
            return;
        }

        switchPlayer();
        fieldOpenedThisTurn = false;
        columnSolvedThisTurn = false;
        enableFieldOpening();
        tvInfo.setText("Na potezu je igrač " + currentPlayer + ". Otvori jedno polje.");
        updateHeader();
    }

    private void guessColumn(int col) {
        if (columnSolved[col] || finalSolved || roundFinished) return;

        if (!hasOpenedFieldInColumn(col)) {
            Toast.makeText(requireContext(), "Prvo otvori neko polje u toj koloni.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!canGuessNow()) {
            Toast.makeText(requireContext(), "Prvo otvori jedno polje.", Toast.LENGTH_SHORT).show();
            return;
        }

        String guess = columnInputs[col].getText().toString().trim();
        String answer = games.get(round - 1).columns.get(col).answer;

        if (guess.isEmpty()) {
            Toast.makeText(requireContext(), "Unesi rešenje kolone", Toast.LENGTH_SHORT).show();
            return;
        }

        if (normalize(guess).equals(normalize(answer))) {
            int points = calculateColumnPoints(col);

            if (multiplayerMode && viewModel != null) {
                viewModel.solveColumn(col, points);
            } else {
                addPoints(points);
            }

            columnSolvedThisTurn = true;
            revealColumn(col);
            disableFieldOpening();
            btnPassTurn.setEnabled(true);

            tvInfo.setText("Tačno! Možeš da klikneš Dalje, pogodiš konačno rešenje ili pogađaš kolone koje imaju otvoreno polje.");
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
        String answer =games.get(round - 1).finalAnswer;

        if (guess.isEmpty()) {
            Toast.makeText(requireContext(), "Unesi konačno rešenje", Toast.LENGTH_SHORT).show();
            return;
        }

        if (normalize(guess).equals(normalize(answer))) {
            int points = calculateFinalPoints();

            if (multiplayerMode && viewModel != null) {
                viewModel.solveFinal(points);
            } else {
                addPoints(points);
            }

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
        columnSolutions[col].setText(games.get(round - 1).columns.get(col).answer);
        guessColumnButtons[col].setEnabled(false);
        columnInputs[col].setEnabled(false);

        for (int row = 0; row < 4; row++) {
            fieldButtons[col][row].setText(games.get(round - 1).columns.get(col).fields.get(row));
            fieldButtons[col][row].setEnabled(false);
        }
    }

    private void revealAllAnswers() {
        for (int col = 0; col < 4; col++) {
            columnSolutions[col].setText(games.get(round - 1).columns.get(col).answer);
            guessColumnButtons[col].setEnabled(false);
            columnInputs[col].setEnabled(false);

            for (int row = 0; row < 4; row++) {
                fieldButtons[col][row].setText(games.get(round - 1).columns.get(col).fields.get(row));
                fieldButtons[col][row].setEnabled(false);
            }
        }

        finalInput.setText(games.get(round - 1).finalAnswer);
        finalInput.setEnabled(false);
        btnGuessFinal.setEnabled(false);
        btnPassTurn.setEnabled(false);
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

    private void startTimer(long endsAtMillis) {
        if (timer != null) timer.cancel();

        long remaining = endsAtMillis - System.currentTimeMillis();

        if (remaining <= 0) {
            tvTimer.setText("00:00");

            if (multiplayerMode && viewModel != null) {
                if (viewModel.amIAuthoritative()) {
                    if ("ROUND_END".equals(currentRemotePhase)) {
                        viewModel.startNextRound();
                    } else if ("PLAYING".equals(currentRemotePhase)) {
                        viewModel.finishRound();
                    }
                }
                return;
            }

            revealAllAnswers();
            finishRound();
            return;
        }

        timer = new CountDownTimer(remaining, 1000) {
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

                if (multiplayerMode && viewModel != null) {
                    if (viewModel.amIAuthoritative()) {
                        if ("ROUND_END".equals(currentRemotePhase)) {
                            viewModel.startNextRound();
                        } else if ("PLAYING".equals(currentRemotePhase)) {
                            viewModel.finishRound();
                        }
                    }
                    return;
                }

                tvInfo.setText("Vreme je isteklo. Rešenja su otkrivena.");
                revealAllAnswers();
                finishRound();
            }
        };

        timer.start();
    }

    private void startTimer() {
        startTimer(System.currentTimeMillis() + 120000);
    }

    private void finishRound() {
        if (multiplayerMode && viewModel != null) {
            revealAllAnswers();
            setAllInputsEnabled(false);
            viewModel.finishRound();
            return;
        }

        if (timer != null) timer.cancel();

        roundFinished = true;
        fieldOpenedThisTurn = false;

        revealAllAnswers();

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

    private boolean hasOpenedFieldInColumn(int col) {
        for (int row = 0; row < 4; row++) {
            if (opened[col][row]) {
                return true;
            }
        }
        return false;
    }

    private void showEndGame() {
        if (alreadyNavigated) return;
        alreadyNavigated = true;
        String winner;

        if (player1Score > player2Score) {
            winner = "Pobednik asocijacija je igrač 1!";
        } else if (player2Score > player1Score) {
            winner = "Pobednik asocijacija je igrač 2!";
        } else {
            winner = "Asocijacije su nerešene!";
        }

        Toast.makeText(requireContext(), winner + " Sledi igra Skocko.", Toast.LENGTH_LONG).show();

        saveAssociationsResult();

        Bundle args = new Bundle();

        if (multiplayerMode) {
            args.putString("sessionId", GameSessionManager.get().getSessionId());
            args.putBoolean("isHost", GameSessionManager.get().isPlayer1());
            args.putString("hostUid", GameSessionManager.get().getPlayer1Uid());
        }

        NavHostFragment.findNavController(this)
                .navigate(R.id.action_associations_to_skocko, args);
    }

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        tvPlayer.setText("Na potezu: igrač " + currentPlayer);
        tvScore.setText(player1Score + " : " + player2Score);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateGameScore(player1Score, player2Score, null, null);
        }
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
        return fieldOpenedThisTurn || columnSolvedThisTurn || !hasOpenableField();
    }

    private String normalize(String text) {
        return text.trim()
                .toLowerCase()
                .replace("č", "c")
                .replace("ć", "c")
                .replace("š", "s")
                .replace("đ", "dj")
                .replace("ž", "z");
    }

    private void saveAssociationsResult() {
        // Snimi u gameResults kao i pre (tuđi kod)
        Map<String, Object> result = new HashMap<>();
        result.put("game", "associations");
        result.put("player1Score", player1Score);
        result.put("player2Score", player2Score);
        result.put("createdAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance()
                .collection("gameResults")
                .add(result);

        // snima statistiku za igrača 1
        if (multiplayerMode) {
            new com.example.sabona.game.GameSessionRepository().isFriendlyMatch((friendly, e) -> {
                if (!Boolean.TRUE.equals(friendly)) {
                    new StatsRepository().saveAsocijacijeResult(player1Score, finalSolved);
                }
            });
        } else {
            new StatsRepository().saveAsocijacijeResult(player1Score, finalSolved);
        }
    }
}