package com.example.sabona.spojnice;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.MainActivity;
import com.example.sabona.R;
import com.example.sabona.spojnice.SpojniceRepository.SpojniceQuestion;
import com.google.firebase.auth.FirebaseAuth;

public class SpojniceFragment extends Fragment {

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo, tvCriteria;
    private Button[] leftButtons  = new Button[5];
    private Button[] rightButtons = new Button[5];
    private TextView[] arrows     = new TextView[5];

    // ── ViewModel ──────────────────────────────────────────────────────────
    private SpojniceViewModel vm;

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupClicks();

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "unknown";

        vm = new ViewModelProvider(this).get(SpojniceViewModel.class);
        vm.init(uid);

        observeViewModel();

        // Čitaj prosleđene argumente od KoZnaZnaFragment
        Bundle args = getArguments();
        if (args != null) {
            String passedSessionId = args.getString("sessionId", "");
            boolean passedIsHost   = args.getBoolean("isHost", true);
            if (!passedSessionId.isEmpty()) {
                // Sesija proslijeđena — učitaj pitanja pa se spoji bez dijaloga
                tvInfo.setText("Učitavanje pitanja...");
                setAllButtonsEnabled(false);
                vm.loadQuestions(() -> {
                    //if (!isAdded()) return;
                    if (passedIsHost) {
                        vm.createSessionSilent(passedSessionId);
                    } else {
                        vm.joinExistingSession(passedSessionId, false);
                    }
                });
                return;
            }
        }

        // Nema prosleđene sesije — učitaj pitanja i prikaži dialog
        tvInfo.setText("Učitavanje pitanja...");
        setAllButtonsEnabled(false);
        vm.loadQuestions(() -> {
            if (isAdded()) showJoinDialog();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Timer i listener se čiste u onCleared() ViewModela
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Observeri
    // ═══════════════════════════════════════════════════════════════════════

    private void observeViewModel() {

        vm.getUiPhase().observe(getViewLifecycleOwner(), phase -> {
            switch (phase) {
                case LOADING:
                    setAllButtonsEnabled(false);
                    tvInfo.setText("Učitavanje...");
                    break;
                case WAITING:
                    setAllButtonsEnabled(false);
                    break;
                case PLAYING:
                    // Board state observer će postaviti dugmad
                    break;
                case GAME_OVER:
                    setAllButtonsEnabled(false);
                    break;
            }
        });

        vm.getWaitingMsg().observe(getViewLifecycleOwner(), msg ->
                tvInfo.setText(msg));

        vm.getInfoText().observe(getViewLifecycleOwner(), msg ->
                tvInfo.setText(msg));

        vm.getTimerText().observe(getViewLifecycleOwner(), text ->
                tvTimer.setText(text));

        vm.getCriteriaText().observe(getViewLifecycleOwner(), text ->
                tvCriteria.setText(text));

        vm.getHeaderState().observe(getViewLifecycleOwner(), state -> {
            tvRound.setText("Runda " + state.round + "/2");
            tvPlayer.setText(state.playerLabel);
            tvScore.setText(state.p1Score + " : " + state.p2Score);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateGameScore(state.p1Score, state.p2Score, null, null);
            }
        });

        // Board state — renderuj cijelu ploču
        vm.getBoardState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            SpojniceQuestion q = vm.getCurrentQuestion();
            if (q == null) return;
            renderBoard(state, q);
        });

        // Jedan par upravo spojen — vizualni update
        vm.getConnectionEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            Integer leftIdx = event.getIfNotHandled();
            if (leftIdx == null) return;
            SpojniceViewModel.BoardState state = vm.getBoardState().getValue();
            SpojniceQuestion q = vm.getCurrentQuestion();
            if (state != null && q != null) {
                renderConnectedPair(leftIdx, state.connectedBy[leftIdx], state.rightDisplayOrder, q);
            }
        });

        // Game over
        vm.getGameOverEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            String summary = event.getIfNotHandled();
            if (summary == null) return;

            Toast.makeText(requireContext(), summary, Toast.LENGTH_LONG).show();
            try {
                Bundle navArgs = new Bundle();
                navArgs.putString("sessionId", vm.getSessionId());
                navArgs.putBoolean("isHost",   vm.isHost());
                navArgs.putString("hostUid",   vm.getMyUid());
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_spojnice_to_associations, navArgs);
            } catch (Exception e) {
                // Navigation fallback
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Renderuj ploču
    // ═══════════════════════════════════════════════════════════════════════

    private void renderBoard(SpojniceViewModel.BoardState state, SpojniceQuestion q) {
        // Lijeva kolona
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(q.leftItems.get(i));

            if (state.connected[i]) {
                renderConnectedPair(i, state.connectedBy[i], state.rightDisplayOrder, q);
            } else if (state.attempted[i]) {
                leftButtons[i].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light));
                leftButtons[i].setEnabled(false);
                arrows[i].setText("✗");
                arrows[i].setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light));
            } else {
                // Selektovano?
                boolean selected = (vm.getSelectedLeft() == i);
                leftButtons[i].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(),
                                selected ? R.color.blue : R.color.white));
                leftButtons[i].setEnabled(state.myTurn);
                arrows[i].setText("→");
                arrows[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_blue));
            }
        }

        // Desna kolona — prema rightDisplayOrder
        for (int pos = 0; pos < 5; pos++) {
            rightButtons[pos].setText(q.rightItems.get(state.rightDisplayOrder[pos]));
        }

        for (int pos = 0; pos < 5; pos++) {
            boolean used = false;
            for (int i = 0; i < 5; i++) {
                if (state.connected[i] && q.correctPairs[i] == state.rightDisplayOrder[pos]) {
                    used = true;
                    break;
                }
            }
            rightButtons[pos].setEnabled(!used && state.myTurn);
            rightButtons[pos].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(),
                            used ? android.R.color.holo_green_dark : R.color.petal));
        }

        // Phase B — lijeva dugmad: samo nespojena su aktivna
        String ph = state.phaseStr;
        if ("r1_phB".equals(ph) || "r2_phB".equals(ph)) {
            for (int i = 0; i < 5; i++) {
                if (!state.connected[i] && !state.attempted[i]) {
                    leftButtons[i].setEnabled(state.myTurn);
                }
            }
        }
    }

    private void renderConnectedPair(int leftIndex, int byPlayer, int[] rightDisplayOrder,
                                     SpojniceQuestion q) {
        leftButtons[leftIndex].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
        leftButtons[leftIndex].setEnabled(false);
        arrows[leftIndex].setText("✓");
        arrows[leftIndex].setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));

        int correctRight = q.correctPairs[leftIndex];
        for (int pos = 0; pos < 5; pos++) {
            if (rightDisplayOrder[pos] == correctRight) {
                rightButtons[pos].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
                rightButtons[pos].setEnabled(false);
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Dialog
    // ═══════════════════════════════════════════════════════════════════════

    private void showJoinDialog() {
        String uid = vm.getMyUid();
        String suggested = uid.length() >= 6 ? uid.substring(0, 6).toUpperCase() : "SPJ01";

        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setTitle("Spojnice");
        b.setMessage("Odaberi način igranja.\nKod sesije: " + suggested);

        EditText input = new EditText(requireContext());
        input.setHint("Kod sesije");
        input.setText(suggested);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        b.setView(input);

        b.setPositiveButton("Kreiraj (Igrač 1)", (d, w) ->
                vm.createSession(input.getText().toString().trim().toUpperCase()));
        b.setNegativeButton("Pridruži se (Igrač 2)", (d, w) ->
                vm.joinSession(input.getText().toString().trim().toUpperCase()));
        b.setCancelable(false);
        b.show();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bind & clicks
    // ═══════════════════════════════════════════════════════════════════════

    private void bindViews(View view) {
        tvRound    = view.findViewById(R.id.tvRound);
        tvPlayer   = view.findViewById(R.id.tvPlayer);
        tvTimer    = view.findViewById(R.id.tvTimer);
        tvScore    = view.findViewById(R.id.tvScore);
        tvInfo     = view.findViewById(R.id.tvInfo);
        tvCriteria = view.findViewById(R.id.tvCriteria);

        leftButtons[0]  = view.findViewById(R.id.btnLeft1);
        leftButtons[1]  = view.findViewById(R.id.btnLeft2);
        leftButtons[2]  = view.findViewById(R.id.btnLeft3);
        leftButtons[3]  = view.findViewById(R.id.btnLeft4);
        leftButtons[4]  = view.findViewById(R.id.btnLeft5);

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
    }

    private void setupClicks() {
        for (int i = 0; i < 5; i++) {
            int idx = i;
            leftButtons[i].setOnClickListener(v  -> vm.onLeftClick(idx));
            rightButtons[i].setOnClickListener(v -> vm.onRightClick(idx));
        }
    }

    private void setAllButtonsEnabled(boolean enabled) {
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setEnabled(enabled);
            rightButtons[i].setEnabled(enabled);
        }
    }
}