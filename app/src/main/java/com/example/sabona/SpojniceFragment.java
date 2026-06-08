package com.example.sabona;

import android.os.Bundle;
import android.os.CountDownTimer;
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
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.repository.SpojniceRepository;
import com.example.sabona.repository.SpojniceRepository.SpojniceQuestion;
import com.example.sabona.repository.StatsRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceFragment extends Fragment {

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView tvRound, tvPlayer, tvTimer, tvScore, tvInfo, tvCriteria;
    private TextView tvRoundScore1, tvRoundScore2, tvConnected;
    private Button[] leftButtons  = new Button[5];
    private Button[] rightButtons = new Button[5];
    private TextView[] arrows     = new TextView[5];

    // ── Firebase ───────────────────────────────────────────────────────────
    private final FirebaseFirestore db   = FirebaseFirestore.getInstance();
    private final FirebaseAuth      auth = FirebaseAuth.getInstance();
    private DocumentReference   sessionRef;
    private ListenerRegistration sessionListener;

    // ── Repository ──────────────────────────────────────────────────────────
    private final SpojniceRepository repository = new SpojniceRepository();

    // ── Pitanja (2 runde) ──────────────────────────────────────────────────
    private List<SpojniceQuestion> questions = new ArrayList<>();

    // ── Session state ──────────────────────────────────────────────────────
    private String  myUid;
    private String  sessionId;
    private boolean isHost;
    private boolean soloMode = false;
    private boolean gameEnded = false;

    // ── Redoslijed desnih dugmadi (shuffled, isti za oba telefona) ──────────
    // Čuvamo u Firestoru kao "rightOrder" list, pa oba telefona vide iste parove
    private int[] rightDisplayOrder = new int[5];

    // ── Faze ──────────────────────────────────────────────────────────────
    // Firestore phase string: "waiting_p2", "r1_phA", "r1_phB", "r2_phA", "r2_phB", "finished"
    private static final String PHASE_WAIT    = "waiting_p2";
    private static final String PHASE_R1_A    = "r1_phA";
    private static final String PHASE_R1_B    = "r1_phB";
    private static final String PHASE_R2_A    = "r2_phA";
    private static final String PHASE_R2_B    = "r2_phB";
    private static final String PHASE_DONE    = "finished";

    // ── Stanje igre ────────────────────────────────────────────────────────
    private int round        = 1;
    private int activePlayer = 1;   // 1 ili 2, ko je trenutno na potezu
    private boolean myTurn   = false;

    private int player1Score = 0;
    private int player2Score = 0;
    private int roundScore1  = 0;
    private int roundScore2  = 0;

    private boolean[] connected   = new boolean[5];
    private int[]     connectedBy = new int[5];
    private int       connectedCount = 0;

    private int selectedLeft  = -1;
    private boolean roundFinished = false;

    private CountDownTimer timer;
    private String currentPhaseStr = "";

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        myUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown";
        bindViews(view);
        setupClicks();
        tvInfo.setText("Učitavanje pitanja...");
        setAllButtonsEnabled(false);
        loadQuestions();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        if (sessionListener != null) sessionListener.remove();
    }

    // ── Bind & clicks ──────────────────────────────────────────────────────

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
            int idx = i;
            leftButtons[i].setOnClickListener(v  -> onLeftClick(idx));
            rightButtons[i].setOnClickListener(v -> onRightClick(idx));
        }
    }

    // ── Učitaj pitanja ─────────────────────────────────────────────────────

    private void loadQuestions() {
        repository.fetchQuestions(new SpojniceRepository.SpojniceCallback() {
            @Override
            public void onSuccess(List<SpojniceQuestion> result) {
                if (!isAdded()) return;
                if (result.size() < 2) {
                    tvInfo.setText("Nema dovoljno pitanja (min. 2).");
                    return;
                }
                Collections.shuffle(result);
                questions.add(result.get(0));
                questions.add(result.get(1));
                showJoinDialog();
            }
            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                tvInfo.setText("Greška: " + e.getMessage());
            }
        });
    }

    // ── Dialog za odabir moda ──────────────────────────────────────────────

    private void showJoinDialog() {
        String suggested = myUid.length() >= 6
                ? myUid.substring(0, 6).toUpperCase() : "SPJ01";

        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setTitle("Spojnice");
        b.setMessage("Odaberi način igranja.\nKod sesije: " + suggested);

        EditText input = new EditText(requireContext());
        input.setHint("Kod sesije");
        input.setText(suggested);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        b.setView(input);

        b.setPositiveButton("Kreiraj (Igrač 1)", (d, w) -> {
            sessionId = input.getText().toString().trim().toUpperCase();
            isHost = true;
            soloMode = false;
            createSession();
        });
        b.setNegativeButton("Pridruži se (Igrač 2)", (d, w) -> {
            sessionId = input.getText().toString().trim().toUpperCase();
            isHost = false;
            soloMode = false;
            joinSession();
        });
        b.setNeutralButton("Solo (1 uređaj)", (d, w) -> {
            soloMode = true;
            isHost = true;
            startSoloGame();
        });
        b.setCancelable(false);
        b.show();
    }

    // ── Firestore session ──────────────────────────────────────────────────

    private void createSession() {
        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("spojnice");

        // Generiši shuffle reda za rundu 1 i rundu 2 — isti za oba telefona
        List<Integer> order1 = shuffledOrder();
        List<Integer> order2 = shuffledOrder();

        Map<String, Object> data = new HashMap<>();
        data.put("phase",        PHASE_WAIT);
        data.put("p1Score",      0);
        data.put("p2Score",      0);
        data.put("connected",    new ArrayList<>(java.util.Arrays.asList(false,false,false,false,false)));
        data.put("connectedBy",  new ArrayList<>(java.util.Arrays.asList(0,0,0,0,0)));
        data.put("connectedCount", 0);
        data.put("round1Order",  order1);
        data.put("round2Order",  order2);
        data.put("hostUid",      myUid);

        sessionRef.set(data)
                .addOnSuccessListener(v -> {
                    tvInfo.setText("Čekam Igrača 2...\nKod: " + sessionId);
                    startListening();
                })
                .addOnFailureListener(e -> tvInfo.setText("Greška: " + e.getMessage()));
    }

    private void joinSession() {
        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("spojnice");

        sessionRef.update("phase", PHASE_R1_A, "guestUid", myUid)
                .addOnSuccessListener(v -> {
                    tvInfo.setText("Pridružen! Počinjemo...");
                    startListening();
                })
                .addOnFailureListener(e -> tvInfo.setText("Sesija nije nađena. Provjeri kod!"));
    }

    private List<Integer> shuffledOrder() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) list.add(i);
        Collections.shuffle(list);
        return list;
    }

    // ── Realtime listener ──────────────────────────────────────────────────

    private void startListening() {
        sessionListener = sessionRef.addSnapshotListener((snap, err) -> {
            if (!isAdded() || snap == null || !snap.exists()) return;

            String newPhase = snap.getString("phase");
            if (newPhase == null) return;

            // Uvijek osvježi scorove
            player1Score = toInt(snap.get("p1Score"));
            player2Score = toInt(snap.get("p2Score"));

            if (PHASE_WAIT.equals(newPhase)) {
                tvInfo.setText("Čekam Igrača 2...\nKod: " + sessionId);
                setAllButtonsEnabled(false);
                return;
            }

            if (PHASE_DONE.equals(newPhase)) {
                showEndGame();
                return;
            }

            // Ako se faza promijenila — postavi stanje iz Firestore
            if (!newPhase.equals(currentPhaseStr)) {
                currentPhaseStr = newPhase;

                // Učitaj connected state iz Firestore
                List<?> connList = (List<?>) snap.get("connected");
                List<?> byList   = (List<?>) snap.get("connectedBy");
                long cCount      = toLong(snap.get("connectedCount"));
                connectedCount   = (int) cCount;

                if (connList != null && byList != null) {
                    for (int i = 0; i < 5; i++) {
                        connected[i]   = Boolean.TRUE.equals(connList.get(i));
                        connectedBy[i] = toInt(byList.get(i));
                    }
                }

                // Učitaj rightDisplayOrder za ovu rundu
                String orderKey = newPhase.startsWith("r1") ? "round1Order" : "round2Order";
                List<?> orderList = (List<?>) snap.get(orderKey);
                if (orderList != null && orderList.size() == 5) {
                    for (int i = 0; i < 5; i++) rightDisplayOrder[i] = toInt(orderList.get(i));
                }

                applyPhase(newPhase);
            } else {
                // Ista faza ali možda je neko nešto spojio — osvježi
                List<?> connList = (List<?>) snap.get("connected");
                List<?> byList   = (List<?>) snap.get("connectedBy");
                long cCount      = toLong(snap.get("connectedCount"));
                if (connList != null && byList != null) {
                    int oldCount = connectedCount;
                    connectedCount = (int) cCount;
                    for (int i = 0; i < 5; i++) {
                        boolean wasConnected = connected[i];
                        connected[i]   = Boolean.TRUE.equals(connList.get(i));
                        connectedBy[i] = toInt(byList.get(i));
                        if (!wasConnected && connected[i]) {
                            // Netko je upravo spojio par i — prikaži vizuelno
                            renderConnectedPair(i, connectedBy[i]);
                            // Ažuriraj roundScore iz Firestore podataka
                            if (connectedBy[i] == 1) roundScore1 += 2;
                            else if (connectedBy[i] == 2) roundScore2 += 2;
                        }
                    }
                    if (connectedCount != oldCount) updateScoreViews();
                }
                updateHeader();
                updateScoreViews();
            }
        });
    }

    // ── Primijeni fazu ─────────────────────────────────────────────────────

    private void applyPhase(String ph) {
        if (timer != null) timer.cancel();
        roundFinished = false;
        selectedLeft  = -1;

        round = ph.startsWith("r1") ? 1 : 2;

        // Ko je aktivan
        // r1_phA: Igrač 1 igra; r1_phB: Igrač 2 igra
        // r2_phA: Igrač 2 igra; r2_phB: Igrač 1 igra
        if (PHASE_R1_A.equals(ph) || PHASE_R2_B.equals(ph)) {
            activePlayer = 1;
        } else {
            activePlayer = 2;
        }

        myTurn = soloMode
                || (isHost  && activePlayer == 1)
                || (!isHost && activePlayer == 2);

        setupRoundUI();
        updateHeader();
        updateScoreViews();

        // Resetuj roundScore samo na početku NOVE runde (Phase A)
        if (PHASE_R1_A.equals(ph) || PHASE_R2_A.equals(ph)) {
            roundScore1 = 0;
            roundScore2 = 0;
            connectedCount = 0;
            for (int i = 0; i < 5; i++) { connected[i] = false; connectedBy[i] = 0; }
            setupRoundUI();
        } else {
            // Phase B — roundScore rekonstruišemo iz već učitanog connected stanja
            roundScore1 = 0;
            roundScore2 = 0;
            for (int i = 0; i < 5; i++) {
                if (connected[i]) {
                    if (connectedBy[i] == 1) roundScore1 += 2;
                    else if (connectedBy[i] == 2) roundScore2 += 2;
                }
            }
        }

        startTimer(30);
    }

    private void setupRoundUI() {
        if (questions.isEmpty()) return;
        SpojniceQuestion q = questions.get(round - 1);
        tvCriteria.setText(q.criteria);

        // Lijeva kolona
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(q.leftItems.get(i));
        }

        // Desna kolona — prema rightDisplayOrder
        for (int pos = 0; pos < 5; pos++) {
            rightButtons[pos].setText(q.rightItems.get(rightDisplayOrder[pos]));
        }

        // Obnovi vizuelno stanje — oboji spojene parove
        for (int i = 0; i < 5; i++) {
            if (connected[i]) {
                renderConnectedPair(i, connectedBy[i]);
            } else {
                leftButtons[i].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.white));
                leftButtons[i].setEnabled(myTurn);
                arrows[i].setText("→");
                arrows[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_blue));
            }
        }

        // Desna dugmad — disable ona koja su spojena
        for (int pos = 0; pos < 5; pos++) {
            boolean used = false;
            for (int i = 0; i < 5; i++) {
                if (connected[i] && q.correctPairs[i] == rightDisplayOrder[pos]) {
                    used = true;
                    break;
                }
            }
            rightButtons[pos].setEnabled(!used && myTurn);
            if (used) {
                rightButtons[pos].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
            } else {
                rightButtons[pos].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.petal));
            }
        }

        // Phase B — lijeva dugmad: samo nespojena su aktivna
        String ph = currentPhaseStr;
        if (PHASE_R1_B.equals(ph) || PHASE_R2_B.equals(ph)) {
            for (int i = 0; i < 5; i++) {
                leftButtons[i].setEnabled(!connected[i] && myTurn);
            }
        }

        tvInfo.setText(myTurn
                ? "🎯 Tvoj red! Klikni lijevo → desno."
                : "⏳ Čekam protivnika...");
    }

    // ── Klikovi ────────────────────────────────────────────────────────────

    private void onLeftClick(int index) {
        if (!myTurn || roundFinished || connected[index]) return;

        if (selectedLeft != -1 && !connected[selectedLeft]) {
            leftButtons[selectedLeft].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
        }
        selectedLeft = index;
        leftButtons[index].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), R.color.blue));

        SpojniceQuestion q = questions.get(round - 1);
        tvInfo.setText("Odabrano: \"" + q.leftItems.get(index) + "\". Klikni par desno.");
    }

    private void onRightClick(int displayPos) {
        if (!myTurn || roundFinished) return;
        if (selectedLeft == -1) {
            Toast.makeText(requireContext(), "Prvo klikni pojam lijevo!", Toast.LENGTH_SHORT).show();
            return;
        }

        int originalRightIndex = rightDisplayOrder[displayPos];
        SpojniceQuestion q = questions.get(round - 1);

        if (q.correctPairs[selectedLeft] == originalRightIndex) {
            // ✓ Tačno — ažuriraj Firestore
            int pairIndex = selectedLeft;
            selectedLeft = -1;

            // Lokalno označi vizuelno odmah (optimistično)
            renderConnectedPair(pairIndex, activePlayer);
            connected[pairIndex]   = true;
            connectedBy[pairIndex] = activePlayer;
            connectedCount++;

            // NE mijenjamo lokalne score varijable ovdje —
            // Firestore listener će ih ažurirati kad stigne FieldValue.increment odgovor.
            // Ovo sprečava race condition između lokalnog i serverskog stanja.

            updateScoreViews();

            // Snimi u Firestore
            saveConnectionToFirestore(pairIndex, connectedCount);

        } else {
            // ✗ Netačno
            leftButtons[selectedLeft].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
            selectedLeft = -1;
            tvInfo.setText("Netačno! Pokušaj ponovo.");
        }
    }

    private void saveConnectionToFirestore(int pairIndex, int newCount) {
        if (soloMode) {
            // Solo — provjeri je li gotovo
            if (newCount == 5) { onAllConnected(); return; }
            return;
        }

        String scoreField = (activePlayer == 1) ? "p1Score" : "p2Score";

        Map<String, Object> update = new HashMap<>();
        update.put("connected." + pairIndex,   true);
        update.put("connectedBy." + pairIndex, activePlayer);
        update.put("connectedCount", newCount);
        // Koristi increment da izbjegnemo race condition (ne pisati apsolutnu vrijednost)
        update.put(scoreField, com.google.firebase.firestore.FieldValue.increment(2));

        sessionRef.update(update).addOnSuccessListener(v -> {
            if (newCount == 5) onAllConnected();
        });
    }

    private void onAllConnected() {
        // Sve spojeno u Phase A → preskoči Phase B
        advancePhase(true);
    }

    // ── Prikaz spojenog para ───────────────────────────────────────────────

    private void renderConnectedPair(int leftIndex, int byPlayer) {
        SpojniceQuestion q = questions.get(round - 1);

        leftButtons[leftIndex].setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), android.R.color.holo_green_dark));
        leftButtons[leftIndex].setEnabled(false);

        arrows[leftIndex].setText("✓");
        arrows[leftIndex].setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));

        // Nađi koji display pos odgovara ovom paru
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

    // ── Timer ──────────────────────────────────────────────────────────────

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer((long) seconds * 1000, 1000) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText(String.format("00:%02d", ms / 1000));
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
        if (roundFinished) return;
        // Samo igrač koji je bio na potezu (ili host u realtime modu) unapređuje fazu.
        // Gost koji čeka NE smije napredovati — Firestore listener će to riješiti.
        if (!soloMode && !myTurn && !isHost) {
            // Nismo na potezu i nismo host — samo resetuj lokalni UI, Firestore će nas obavijestiti
            tvInfo.setText("Čekam...");
            return;
        }
        advancePhase(false);
    }

    // ── Napredovanje faze ──────────────────────────────────────────────────
    // allConnected = true ako je sve spojeno, false = tajmer istekao

    private void advancePhase(boolean allConnected) {
        if (timer != null) timer.cancel();
        roundFinished = true;

        String nextPhase;
        String ph = currentPhaseStr;

        if (PHASE_R1_A.equals(ph)) {
            // Provjeri ima li nepovezanih
            int remaining = 0;
            for (boolean c : connected) if (!c) remaining++;
            nextPhase = (allConnected || remaining == 0) ? PHASE_R2_A : PHASE_R1_B;
        } else if (PHASE_R1_B.equals(ph)) {
            nextPhase = PHASE_R2_A;
        } else if (PHASE_R2_A.equals(ph)) {
            int remaining = 0;
            for (boolean c : connected) if (!c) remaining++;
            nextPhase = (allConnected || remaining == 0) ? PHASE_DONE : PHASE_R2_B;
        } else {
            // PHASE_R2_B
            nextPhase = PHASE_DONE;
        }

        String finalNextPhase = nextPhase;

        if (soloMode) {
            // Solo — direktan prelaz sa 3s pauzom
            new CountDownTimer(3000, 1000) {
                @Override public void onTick(long ms) {
                    tvInfo.setText("Nastavak za: " + (ms/1000+1) + "s...");
                }
                @Override public void onFinish() {
                    if (!isAdded()) return;
                    currentPhaseStr = finalNextPhase;
                    if (PHASE_DONE.equals(finalNextPhase)) { showEndGame(); return; }
                    // Reset za novu rundu ako prelazimo na r2
                    if (PHASE_R2_A.equals(finalNextPhase)) {
                        connectedCount = 0;
                        for (int i = 0; i < 5; i++) { connected[i] = false; connectedBy[i] = 0; }
                        // Generiši novi redosled za rundu 2
                        List<Integer> newOrder = shuffledOrder();
                        for (int i = 0; i < 5; i++) rightDisplayOrder[i] = newOrder.get(i);
                    }
                    applyPhase(finalNextPhase);
                }
            }.start();
        } else {
            // Realtime — samo host mijenja fazu u Firestoru
            if (isHost) {
                Map<String, Object> update = new HashMap<>();
                update.put("phase", nextPhase);
                // NE pisati p1Score/p2Score ovdje — već su upisani kroz FieldValue.increment
                // u saveConnectionToFirestore(). Pisanje apsolutnih vrijednosti uzrokovalo bi
                // race condition i pregaženje bodova.
                if (PHASE_R2_A.equals(nextPhase)) {
                    update.put("connected",    new ArrayList<>(java.util.Arrays.asList(false,false,false,false,false)));
                    update.put("connectedBy",  new ArrayList<>(java.util.Arrays.asList(0,0,0,0,0)));
                    update.put("connectedCount", 0);
                }
                sessionRef.update(update);
            }
            // Prikaži pauzu oboje dok Firestore listener ne dođe
            tvInfo.setText("Runda završena...");
            setAllButtonsEnabled(false);
        }
    }

    // ── Kraj igre ──────────────────────────────────────────────────────────

    private void showEndGame() {
        if (gameEnded) return;
        gameEnded = true;
        if (timer != null) timer.cancel();
        if (!isAdded()) return;

        int myScore      = isHost ? player1Score : player2Score;
        int myConnected  = myScore / 2;  // svaki spojeni par = 2 boda
        new StatsRepository().saveSpojniceResult(myConnected, 10, myScore);

        String result;
        if (player1Score > player2Score)
            result = "Pobjednik spojnica: Igrač 1! (" + player1Score + " vs " + player2Score + ")";
        else if (player2Score > player1Score)
            result = "Pobjednik spojnica: Igrač 2! (" + player2Score + " vs " + player1Score + ")";
        else
            result = "Spojnice izjednačene! (" + player1Score + " : " + player2Score + ")";

        Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show();
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_spojnice_to_associations);
    }

    // ── Solo mod ───────────────────────────────────────────────────────────

    private void startSoloGame() {
        player1Score = 0;
        player2Score = 0;
        // Generiši oba reda lokalno
        List<Integer> order1 = shuffledOrder();
        List<Integer> order2 = shuffledOrder();
        // Sačuvaj ih (solo nema Firestore, pa samo koristimo round1/2Order lokalne liste)
        // Pohrani round2Order u posebnu varijablu
        savedRound2Order = new int[5];
        for (int i = 0; i < 5; i++) {
            rightDisplayOrder[i] = order1.get(i);
            savedRound2Order[i]  = order2.get(i);
        }
        currentPhaseStr = PHASE_R1_A;
        applyPhase(PHASE_R1_A);
    }

    private int[] savedRound2Order = new int[5];

    // ── Helpers ────────────────────────────────────────────────────────────

    private void updateHeader() {
        tvRound.setText("Runda " + round + "/2");
        String label;
        switch (currentPhaseStr) {
            case PHASE_R1_A: label = "Na potezu: Igrač 1 (1. faza)"; break;
            case PHASE_R1_B: label = "Na potezu: Igrač 2 (preostali)"; break;
            case PHASE_R2_A: label = "Na potezu: Igrač 2 (1. faza)"; break;
            case PHASE_R2_B: label = "Na potezu: Igrač 1 (preostali)"; break;
            default:         label = "Runda završena"; break;
        }
        tvPlayer.setText(label);
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

    private int  toInt(Object v)  { return (int) toLong(v); }
    private long toLong(Object v) {
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Boolean) return Boolean.TRUE.equals(v) ? 1L : 0L;
        return 0L;
    }
}