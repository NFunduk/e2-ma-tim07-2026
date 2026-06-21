package com.example.sabona.koznazna;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.MainActivity;
import com.example.sabona.R;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Ko Zna Zna — prezentacijski sloj.
 *
 * Fragment sada samo:
 *   1. Observuje LiveData iz KoZnaZnaViewModel
 *   2. Ažurira Views
 *   3. Prosljeđuje korisničke akcije ViewModelu
 *
 * Sva logika igre, Firestore, timer i bodovanje su u KoZnaZnaViewModel.
 */
public class KoZnaZnaFragment extends Fragment {

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView tvQuestionNum, tvTimer, tvRound, tvTurn, tvScore, tvInfo, tvQuestion;
    private TextView tvPlayer1Status, tvPlayer2Status, tvScore1, tvScore2;
    private Button[] answerButtons = new Button[4];
    private ProgressBar progressBar;
    private View layoutGame, layoutWaiting;
    private TextView tvWaitingMsg;

    // ── ViewModel ──────────────────────────────────────────────────────────
    private KoZnaZnaViewModel vm;

    // ── Lokalni state (samo UI) ─────────────────────────────────────────────
    // Index dugmeta koji je ovaj igrač pritisnuo (za reset boje pri novom pitanju)
    private int myPressedButtonIndex = -1;

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        tvQuestionNum   = view.findViewById(R.id.tvQuestionNum);
        tvTimer         = view.findViewById(R.id.tvTimer);
        tvRound         = view.findViewById(R.id.tvRound);
        tvTurn          = view.findViewById(R.id.tvTurn);
        tvScore         = view.findViewById(R.id.tvScore);
        tvInfo          = view.findViewById(R.id.tvInfo);
        tvQuestion      = view.findViewById(R.id.tvQuestion);
        tvPlayer1Status = view.findViewById(R.id.tvPlayer1Status);
        tvPlayer2Status = view.findViewById(R.id.tvPlayer2Status);
        tvScore1        = view.findViewById(R.id.tvScore1);
        tvScore2        = view.findViewById(R.id.tvScore2);
        progressBar     = view.findViewById(R.id.progressBar);
        layoutGame      = view.findViewById(R.id.layoutGame);
        layoutWaiting   = view.findViewById(R.id.layoutWaiting);
        tvWaitingMsg    = view.findViewById(R.id.tvWaitingMsg);

        answerButtons[0] = view.findViewById(R.id.btnAnswer1);
        answerButtons[1] = view.findViewById(R.id.btnAnswer2);
        answerButtons[2] = view.findViewById(R.id.btnAnswer3);
        answerButtons[3] = view.findViewById(R.id.btnAnswer4);

        // Postavi klik listenere jednom ovdje
        for (int i = 0; i < 4; i++) {
            int idx = i;
            answerButtons[i].setOnClickListener(v -> vm.onAnswerClick(idx));
        }

        // Init ViewModel
        vm = new ViewModelProvider(this).get(KoZnaZnaViewModel.class);
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "unknown";

        Bundle passedArgs = getArguments();
        if (passedArgs != null && !passedArgs.getString("sessionId", "").isEmpty()) {
            String sessionId = passedArgs.getString("sessionId");
            boolean isHost    = passedArgs.getBoolean("isHost", true);
            String hostUid    = passedArgs.getString("hostUid", "");

            if (isHost) {
                com.example.sabona.game.GameSessionManager.get().setupAsHost(sessionId);
            } else {
                com.example.sabona.game.GameSessionManager.get().setupAsGuest(sessionId, hostUid);
            }

            vm.initWithSession(uid, sessionId, isHost);
        } else {
            vm.init(uid);
        }

        observeViewModel();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Observeri
    // ═══════════════════════════════════════════════════════════════════════

    private void observeViewModel() {

        // Faza — kontroliše vidljivost layouta
        vm.getPhase().observe(getViewLifecycleOwner(), phase -> {
            switch (phase) {
                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    layoutGame.setVisibility(View.GONE);
                    if (layoutWaiting != null) layoutWaiting.setVisibility(View.GONE);
                    break;
                case WAITING_P2:
                    progressBar.setVisibility(View.GONE);
                    layoutGame.setVisibility(View.GONE);
                    if (layoutWaiting != null) layoutWaiting.setVisibility(View.VISIBLE);
                    break;
                case QUESTION:
                case RESULT:
                    progressBar.setVisibility(View.GONE);
                    layoutGame.setVisibility(View.VISIBLE);
                    if (layoutWaiting != null) layoutWaiting.setVisibility(View.GONE);
                    break;
                case GAME_OVER:
                    // gameOverEvent će obraditi navigaciju
                    break;
            }
        });

        // Tekst čekanja (waiting screen)
        vm.getWaitingMsg().observe(getViewLifecycleOwner(), msg -> {
            if (tvWaitingMsg != null) tvWaitingMsg.setText(msg);
        });

        // Timer
        vm.getTimerText().observe(getViewLifecycleOwner(), text ->
                tvTimer.setText(text));

        vm.getTimerWarning().observe(getViewLifecycleOwner(), warning ->
                tvTimer.setTextColor(ContextCompat.getColor(requireContext(),
                        warning ? android.R.color.holo_red_light : R.color.dark_blue)));

        // Skorovi
        vm.getPlayer1Score().observe(getViewLifecycleOwner(), s -> {
            tvScore1.setText(s + " bod.");
            updateScoreBar();
            updateToolbarScore();
        });
        vm.getPlayer2Score().observe(getViewLifecycleOwner(), s -> {
            tvScore2.setText(s + " bod.");
            updateScoreBar();
            updateToolbarScore();
        });

        // Status igrača
        vm.getP1Status().observe(getViewLifecycleOwner(), s -> tvPlayer1Status.setText(s));
        vm.getP2Status().observe(getViewLifecycleOwner(), s -> tvPlayer2Status.setText(s));

        // Info tekst
        vm.getInfoText().observe(getViewLifecycleOwner(), s -> tvInfo.setText(s));

        // Novo pitanje — renderuj Views
        vm.getQuestionEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            Integer qIndex = event.getIfNotHandled();
            if (qIndex == null) return;
            renderQuestionUI(qIndex);
        });

        // Feedback na klik (zelena/crvena boja dugmeta)
        vm.getAnswerFeedback().observe(getViewLifecycleOwner(), feedback -> {
            if (feedback == null) return;
            myPressedButtonIndex = feedback.index;
            answerButtons[feedback.index].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(),
                            feedback.correct ? android.R.color.holo_green_dark
                                    : android.R.color.holo_red_light));
            setButtonsEnabled(false);
        });

        // Tačan odgovor (prikaži zeleno dugme na kraju pitanja)
        vm.getCorrectIndex().observe(getViewLifecycleOwner(), idx -> {
            if (idx == null || idx < 0) return;
            markCorrectAnswer(idx);
        });

        // Result event (kraj jednog pitanja)
        vm.getResultEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            if (event.getIfNotHandled() == null) return;
            setButtonsEnabled(false);
        });

        // Game over event → navigacija
        vm.getGameOverEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            String summary = event.getIfNotHandled();
            if (summary == null) return;

            Toast.makeText(requireContext(), summary, Toast.LENGTH_LONG).show();
            try {
                Bundle args = new Bundle();
                args.putString("sessionId", vm.getSessionId());
                args.putBoolean("isHost",   vm.isHost());
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_kozna_to_spojnice, args);
            } catch (Exception e) {
                // Navigation fallback
            }
        });

        // Dialog za mod — pokreni kad pitanja budu učitana (WAITING_P2 po prvi put)
        // Koristimo jednom-okidajući observer
        /*vm.getPhase().observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<KoZnaZnaViewModel.Phase>() {
            boolean dialogShown = false;
            @Override
            public void onChanged(KoZnaZnaViewModel.Phase phase) {
                if (!dialogShown && phase == KoZnaZnaViewModel.Phase.WAITING_P2) {
                    if (vm.getSessionId() == null || vm.getSessionId().isEmpty()) {
                        // Proveri da nismo došli iz args (prijateljska partija ili matchmaking)
                        Bundle args = getArguments();
                        boolean hasSessionFromArgs = args != null &&
                                !args.getString("sessionId", "").isEmpty();
                        if (!hasSessionFromArgs) {
                            dialogShown = true;
                            showJoinDialog();
                        }
                    }
                }
            }
        });*/
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Renderuj UI pitanja
    // ═══════════════════════════════════════════════════════════════════════

    private void renderQuestionUI(int qIndex) {
        myPressedButtonIndex = -1;

        KoZnaZnaRepository.Question q = vm.getCurrentQuestionData();
        if (q == null) return;

        tvQuestion.setText(q.question);
        tvQuestionNum.setText("Pitanje " + (qIndex + 1) + "/" + vm.getTotalQuestions());
        tvRound.setText("Runda 1/1");
        tvTurn.setText("Na potezu: oba");

        for (int i = 0; i < 4; i++) {
            answerButtons[i].setText(q.answers.get(i));
            resetButtonStyle(i);
        }

        setButtonsEnabled(true);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Dialog za mod
    // ═══════════════════════════════════════════════════════════════════════

    /*private void showJoinDialog() {
        String uid = vm.getMyUid();
        String suggested = uid.length() >= 6
                ? uid.substring(0, 6).toUpperCase() : "TEST01";

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Ko zna zna");
        builder.setMessage("Odaberi način igranja.\nKod sesije: " + suggested);

        final EditText input = new EditText(requireContext());
        input.setHint("Kod sesije");
        input.setText(suggested);
        builder.setView(input);

        builder.setPositiveButton("Kreiraj (Igrač 1)", (d, w) -> {
            String sid = input.getText().toString().trim().toUpperCase();
            vm.createSession(sid);
        });
        builder.setNegativeButton("Pridruži se (Igrač 2)", (d, w) -> {
            String sid = input.getText().toString().trim().toUpperCase();
            vm.joinSession(sid);
        });
        builder.setNeutralButton("Solo test (1 uređaj)", (d, w) ->
                vm.startSoloGame());
        builder.setCancelable(false);
        builder.show();
    }*/

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private void updateScoreBar() {
        Integer s1 = vm.getPlayer1Score().getValue();
        Integer s2 = vm.getPlayer2Score().getValue();
        if (tvScore != null && s1 != null && s2 != null) {
            tvScore.setText("Igrač 1: " + s1 + "  |  Igrač 2: " + s2);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Button btn : answerButtons) btn.setEnabled(enabled);
    }

    private void resetButtonStyle(int idx) {
        answerButtons[idx].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), R.color.white));
        answerButtons[idx].setTextColor(
                ContextCompat.getColor(requireContext(), R.color.dark_blue));
        answerButtons[idx].setEnabled(true);
    }

    private void markCorrectAnswer(int idx) {
        if (idx < 0 || idx >= answerButtons.length) return;
        answerButtons[idx].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
        answerButtons[idx].setTextColor(
                ContextCompat.getColor(requireContext(), R.color.white));
    }

    private void updateToolbarScore() {
        Integer s1 = vm.getPlayer1Score().getValue();
        Integer s2 = vm.getPlayer2Score().getValue();
        if (s1 != null && s2 != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateGameScore(s1, s2, null, null);
        }
    }
}