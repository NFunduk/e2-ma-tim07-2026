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

import com.example.sabona.repository.SpojniceRepository;
import com.example.sabona.repository.SpojniceRepository.SpojniceQuestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpojniceFragment extends Fragment {

    // ---- Views ----
    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo, tvCriteria;
    private TextView tvRoundScore1, tvRoundScore2, tvConnected;
    private Button[] leftButtons  = new Button[5];
    private Button[] rightButtons = new Button[5];
    private TextView[] arrows     = new TextView[5];
    // btnNextRound uklonjen — prelaz je automatski

    // ---- Repository ----
    private final SpojniceRepository repository = new SpojniceRepository();

    // ---- Pitanja učitana iz Firestorea (2 pitanja = 2 runde) ----
    private List<SpojniceQuestion> questions = new ArrayList<>();

    // ---- Redoslijed desnih dugmadi (shuffled prikaz) ----
    // rightDisplayOrder[i] = koji originalni rightItems index je prikazan na poziciji i
    private int[] rightDisplayOrder = new int[5];

    // ---- Stanje igre ----
    private int round = 1;           // 1 ili 2

    /*
     * Faze unutar jedne runde (po specifikaciji):
     *   PHASE_PLAYER_A  – prvi igrač runde igra (30s)
     *   PHASE_PLAYER_B  – drugi igrač dobija preostale pojmove (30s)
     *   PHASE_DONE      – runda završena, pauza prije sljedeće
     */
    private static final int PHASE_PLAYER_A = 0;
    private static final int PHASE_PLAYER_B = 1;
    private static final int PHASE_DONE     = 2;
    private int phase = PHASE_PLAYER_A;

    // Ko je "igrač A" u rundama: runda 1 → igrač 1 počinje; runda 2 → igrač 2 počinje
    private int roundStarterPlayer; // 1 ili 2
    private int activePlayer;       // trenutno ko igra

    // Ukupni bodovi (zbir kroz obje runde)
    private int player1Score = 0;
    private int player2Score = 0;

    // Bodovi unutar trenutne runde (za prikaz u donjoj kartici)
    private int roundScore1 = 0;
    private int roundScore2 = 0;

    // Stanje pojmova u trenutnoj rundi
    private boolean[] connected    = new boolean[5];   // da li je par i spojen
    private int[]     connectedBy  = new int[5];       // koji igrač je spojio par i
    private int       connectedCount = 0;

    // Trenutno odabrani lijevi pojam (-1 = ništa)
    private int selectedLeft = -1;

    private CountDownTimer timer;
    private boolean roundFinished = false;

    // ---- Lifecycle ----

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
        showLoading();
        loadQuestionsFromFirestore();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
    }

    // ---- Inicijalizacija ----

    private void bindViews(View view) {
        tvRound       = view.findViewById(R.id.tvRound);
        tvPlayer      = view.findViewById(R.id.tvPlayer);
        tvTimer       = view.findViewById(R.id.tvTimer);
        tvScore       = view.findViewById(R.id.tvScore);
        tvInfo        = view.findViewById(R.id.tvInfo);
        tvCriteria    = view.findViewById(R.id.tvCriteria);
        tvRoundScore1 = view.findViewById(R.id.tvRoundScore1);
        tvRoundScore2 = view.findViewById(R.id.tvRoundScore2);
        tvConnected   = view.findViewById(R.id.tvConnected);

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
            int index = i;
            leftButtons[i].setOnClickListener(v  -> onLeftClick(index));
            rightButtons[i].setOnClickListener(v -> onRightClick(index));
        }
    }

    private void showLoading() {
        tvInfo.setText("Učitavanje pitanja...");
        setAllButtonsEnabled(false);
    }

    // ---- Firestore ----

    private void loadQuestionsFromFirestore() {
        repository.fetchQuestions(new SpojniceRepository.SpojniceCallback() {
            @Override
            public void onSuccess(List<SpojniceQuestion> result) {
                if (!isAdded()) return;
                if (result.size() < 2) {
                    tvInfo.setText("Nema dovoljno pitanja u bazi (potrebno min. 2).");
                    return;
                }
                // Uzmi nasumično 2 pitanja
                Collections.shuffle(result);
                questions.add(result.get(0));
                questions.add(result.get(1));

                round  = 1;
                player1Score = 0;
                player2Score = 0;
                startRound();
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                tvInfo.setText("Greška pri učitavanju: " + e.getMessage());
            }
        });
    }

    // ---- Logika runde (po specifikaciji) ----

    /**
     * Pokretanje runde.
     * Runda 1: Igrač 1 je starter (Phase A), pa ako ostane nešto → Igrač 2 (Phase B).
     * Runda 2: Igrač 2 je starter (Phase A), pa ako ostane nešto → Igrač 1 (Phase B).
     */
    private void startRound() {
        if (timer != null) timer.cancel();

        phase         = PHASE_PLAYER_A;
        roundStarterPlayer = (round == 1) ? 1 : 2;
        activePlayer  = roundStarterPlayer;

        selectedLeft  = -1;
        connectedCount = 0;
        roundFinished  = false;
        roundScore1    = 0;
        roundScore2    = 0;

        for (int i = 0; i < 5; i++) {
            connected[i]   = false;
            connectedBy[i] = 0;
        }

        setupRoundButtons();
        updateHeader();
        updateScoreViews();

        startTimer(30);
    }

    /**
     * Postavi tekst na dugmadima i shuffluj desnu kolonu.
     * rightDisplayOrder[displayPos] = originalIndex u rightItems
     */
    private void setupRoundButtons() {
        SpojniceQuestion q = questions.get(round - 1);
        tvCriteria.setText(q.criteria);
        tvInfo.setText("Igrač " + activePlayer + " povezuje pojmove. Klikni lijevo, pa desno.");

        // Postavi lijevu kolonu
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(q.leftItems.get(i));
            leftButtons[i].setEnabled(true);
            leftButtons[i].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
        }

        // Shuffle desna kolona
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 5; i++) indices.add(i);
        Collections.shuffle(indices);
        for (int pos = 0; pos < 5; pos++) {
            rightDisplayOrder[pos] = indices.get(pos);
            rightButtons[pos].setText(q.rightItems.get(rightDisplayOrder[pos]));
            rightButtons[pos].setEnabled(true);
            rightButtons[pos].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.petal));
        }

        for (int i = 0; i < 5; i++) {
            arrows[i].setText("→");
            arrows[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_blue));
        }
    }

    /**
     * Prelaz na Phase B: drugi igrač dobija samo nepovezane pojmove.
     * Lijeva dugmad koja su već spojena — disable; ostala ostaju aktivna.
     */
    private void startPhaseB() {
        if (timer != null) timer.cancel();

        phase        = PHASE_PLAYER_B;
        activePlayer = (roundStarterPlayer == 1) ? 2 : 1;
        selectedLeft = -1;

        // Provjeri ima li uopšte nepovezanih
        int remaining = 0;
        for (boolean c : connected) if (!c) remaining++;

        updateHeader();

        if (remaining == 0) {
            // Sve je spojeno u Phase A — preskoči Phase B
            finishRound();
            return;
        }

        // Aktivan samo ono što nije spojeno
        for (int i = 0; i < 5; i++) {
            if (connected[i]) {
                leftButtons[i].setEnabled(false);
            } else {
                leftButtons[i].setEnabled(true);
                leftButtons[i].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.white));
            }
        }
        // Desna dugmad: disable ona koja su već spojena
        for (int pos = 0; pos < 5; pos++) {
            boolean alreadyUsed = false;
            for (int i = 0; i < 5; i++) {
                if (connected[i] && questions.get(round - 1).correctPairs[i] == rightDisplayOrder[pos]) {
                    alreadyUsed = true;
                    break;
                }
            }
            rightButtons[pos].setEnabled(!alreadyUsed);
        }

        tvInfo.setText("Igrač " + activePlayer + " dobija preostale pojmove (" + remaining + ")!");
        startTimer(30);
    }

    private void finishRound() {
        if (timer != null) timer.cancel();
        roundFinished = true;
        phase = PHASE_DONE;
        setAllButtonsEnabled(false);
        updateHeader();

        String summary = buildRoundSummary();

        // Automatski prelaz: 3s pauza sa odbrojavanjem, bez dugmeta
        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long s = millisUntilFinished / 1000 + 1;
                tvInfo.setText(summary + "\nNastavak za: " + s + "s...");
            }

            @Override
            public void onFinish() {
                if (!isAdded()) return;
                if (round == 1) {
                    round = 2;
                    startRound();
                } else {
                    showEndGame();
                }
            }
        }.start();
    }

    private String buildRoundSummary() {
        return String.format("Runda %d završena! Igrač 1: +%d bod  |  Igrač 2: +%d bod",
                round, roundScore1, roundScore2);
    }

    // ---- Klikovi igrača ----

    private void onLeftClick(int index) {
        if (roundFinished || connected[index]) return;

        // Resetuj prethodni odabir
        if (selectedLeft != -1 && !connected[selectedLeft]) {
            leftButtons[selectedLeft].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
        }

        selectedLeft = index;
        leftButtons[index].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), R.color.blue));

        SpojniceQuestion q = questions.get(round - 1);
        tvInfo.setText("Odabrano: \"" + q.leftItems.get(index) + "\". Sada klikni odgovarajući par desno.");
    }

    private void onRightClick(int displayPos) {
        if (roundFinished) return;

        if (selectedLeft == -1) {
            Toast.makeText(requireContext(), "Prvo klikni pojam s lijeve strane!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Koji je originalni index odabranog desnog dugmeta
        int originalRightIndex = rightDisplayOrder[displayPos];
        SpojniceQuestion q = questions.get(round - 1);

        if (q.correctPairs[selectedLeft] == originalRightIndex) {
            // ✓ TAČNO
            connected[selectedLeft]   = true;
            connectedBy[selectedLeft] = activePlayer;
            connectedCount++;

            leftButtons[selectedLeft].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
            rightButtons[displayPos].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
            leftButtons[selectedLeft].setEnabled(false);
            rightButtons[displayPos].setEnabled(false);

            arrows[selectedLeft].setText("✓");
            arrows[selectedLeft].setTextColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));

            // Dodaj bodove aktivnom igraču
            if (activePlayer == 1) {
                roundScore1   += 2;
                player1Score  += 2;
            } else {
                roundScore2   += 2;
                player2Score  += 2;
            }

            selectedLeft = -1;
            tvInfo.setText("Tačno! +2 boda za Igrača " + activePlayer);
            updateScoreViews();

            // Ako je sve spojeno u Phase A → preskoči Phase B
            if (connectedCount == 5) {
                finishRound();
                return;
            }

        } else {
            // ✗ NETAČNO — samo resetuj odabir, ne kažnjavaj
            leftButtons[selectedLeft].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
            selectedLeft = -1;
            tvInfo.setText("Netačno! Pokušaj ponovo.");
        }

        updateHeader();
    }

    // ---- Timer ----

    private void startTimer(int seconds) {
        long millis = (long) seconds * 1000;
        timer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long s = millisUntilFinished / 1000;
                tvTimer.setText(String.format("00:%02d", s));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("00:00");
                onTimerExpired();
            }
        };
        timer.start();
    }

    private void onTimerExpired() {
        if (phase == PHASE_PLAYER_A) {
            // Igrač A nije završio — daj šansu igraču B za preostale
            tvInfo.setText("Vrijeme isteklo za Igrača " + activePlayer + "! Red na Igraču "
                    + ((roundStarterPlayer == 1) ? 2 : 1) + ".");
            startPhaseB();
        } else {
            // Phase B gotova — runda završena
            finishRound();
        }
    }

    // ---- Kraj igre ----

    private void showEndGame() {
        String result;
        if (player1Score > player2Score) {
            result = "Pobjednik spojnica: Igrač 1! (" + player1Score + " vs " + player2Score + ")";
        } else if (player2Score > player1Score) {
            result = "Pobjednik spojnica: Igrač 2! (" + player2Score + " vs " + player1Score + ")";
        } else {
            result = "Spojnice su izjednačene! (" + player1Score + " : " + player2Score + ")";
        }

        Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show();

        NavHostFragment.findNavController(this)
                .navigate(R.id.action_spojnice_to_associations);
    }

    // ---- Pomocne metode ----

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        String phaseLabel = (phase == PHASE_PLAYER_A)
                ? "Na potezu: Igrač " + activePlayer + " (1. faza)"
                : (phase == PHASE_PLAYER_B)
                ? "Na potezu: Igrač " + activePlayer + " (preostali)"
                : "Runda završena";
        tvPlayer.setText(phaseLabel);
        tvScore.setText("Igrač 1: " + player1Score + "  |  Igrač 2: " + player2Score);
    }

    private void updateScoreViews() {
        tvRoundScore1.setText(roundScore1 + " bod.");
        tvRoundScore2.setText(roundScore2 + " bod.");
        tvConnected.setText(connectedCount + "/5");
    }

    private void setAllButtonsEnabled(boolean enabled) {
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setEnabled(enabled);
            rightButtons[i].setEnabled(enabled);
        }
    }
}