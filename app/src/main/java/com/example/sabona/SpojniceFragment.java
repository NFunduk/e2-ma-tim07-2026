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
   // private TextView tvRoundScore1, tvRoundScore2, tvConnected;
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

    // ── Pitanja — SVI učitani, pa mapiramo po docId ────────────────────────
    private List<SpojniceQuestion> allQuestions = new ArrayList<>();
    // Finalna 2 pitanja u ispravnom redosledu (isti na oba telefona)
    private List<SpojniceQuestion> questions = new ArrayList<>();

    // ── Session state ──────────────────────────────────────────────────────
    private String  myUid;
    private String  sessionId;
    private boolean isHost;
    private boolean soloMode  = false;
    private boolean gameEnded = false;

    // ── Redoslijed desnih dugmadi (shuffled, isti za oba telefona) ──────────
    private int[] rightDisplayOrder = new int[5];
    private int[] savedRound2Order  = new int[5]; // samo za solo mod

    // ── Faze ──────────────────────────────────────────────────────────────
    private static final String PHASE_WAIT = "waiting_p2";
    private static final String PHASE_R1_A = "r1_phA";
    private static final String PHASE_R1_B = "r1_phB";
    private static final String PHASE_R2_A = "r2_phA";
    private static final String PHASE_R2_B = "r2_phB";
    private static final String PHASE_DONE = "finished";

    // ── Stanje igre ────────────────────────────────────────────────────────
    private int round        = 1;
    private int activePlayer = 1;
    private boolean myTurn   = false;

    private int player1Score = 0;
    private int player2Score = 0;
    private int roundScore1  = 0;
    private int roundScore2  = 0;

    private boolean[] connected   = new boolean[5];
    private int[]     connectedBy = new int[5];
    private int       connectedCount = 0;

    private int     selectedLeft  = -1;
    private boolean roundFinished = false;

    private CountDownTimer timer;
    private String currentPhaseStr = "";

    private boolean[] attempted = new boolean[5]; // lijevi pojmovi koje je igrač već pokušao spojiti

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────

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

        // Čitaj sessionId i isHost prosleđene od KoZnaZnaFragment
        Bundle passedArgs = getArguments();
        if (passedArgs != null) {
            String passedSessionId = passedArgs.getString("sessionId", "");
            boolean passedIsHost   = passedArgs.getBoolean("isHost", true);
            if (!passedSessionId.isEmpty()) {
                // Sesija je već kreirana u KoZnaZna — preskoči dialog
                sessionId = passedSessionId;
                isHost    = passedIsHost;
                soloMode  = false;
                loadQuestionsAndJoinExistingSession();
                return;
            }
        }

        // Nema prosleđene sesije — pokaži dialog (solo ili direktan start)
        loadQuestions();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        if (sessionListener != null) sessionListener.remove();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Bind & clicks
    // ─────────────────────────────────────────────────────────────────────

    private void bindViews(View view) {
        tvRound       = view.findViewById(R.id.tvRound);
        tvPlayer      = view.findViewById(R.id.tvPlayer);
        tvTimer       = view.findViewById(R.id.tvTimer);
        tvScore       = view.findViewById(R.id.tvScore);
        tvInfo        = view.findViewById(R.id.tvInfo);
        tvCriteria    = view.findViewById(R.id.tvCriteria);
       // tvRoundScore1 = view.findViewById(R.id.tvRoundScore1);
        // tvRoundScore2 = view.findViewById(R.id.tvRoundScore2);
        //tvConnected   = view.findViewById(R.id.tvConnected);

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

    // ─────────────────────────────────────────────────────────────────────
    // Učitaj SVA pitanja sa docId
    // ─────────────────────────────────────────────────────────────────────

    private void loadQuestions() {
        repository.fetchQuestionsWithIds(new SpojniceRepository.SpojniceWithIdsCallback() {
            @Override
            public void onSuccess(List<SpojniceQuestion> result) {
                if (!isAdded()) return;
                if (result.size() < 2) {
                    tvInfo.setText("Nema dovoljno pitanja (min. 2).");
                    return;
                }
                allQuestions = result;
                showJoinDialog();
            }
            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                tvInfo.setText("Greška: " + e.getMessage());
            }
        });
    }

    /**
     * Koristi se kada je sesija već kreirana u KoZnaZna.
     * Učitava pitanja, pa se odmah spaja na postojeću sesiju bez dijaloga.
     * Host već čeka u Firestore (waiting_p2) — guest samo učitava pitanja i mijenja fazu.
     * Host takđer samo učitava pitanja i počinje slušanje (ne treba ništa mijenjati u Firestore).
     */
    private void loadQuestionsAndJoinExistingSession() {
        repository.fetchQuestionsWithIds(new SpojniceRepository.SpojniceWithIdsCallback() {
            @Override
            public void onSuccess(List<SpojniceQuestion> result) {
                if (!isAdded()) return;
                if (result.size() < 2) {
                    tvInfo.setText("Nema dovoljno pitanja (min. 2).");
                    return;
                }
                allQuestions = result;

                // Spoji se na sesiju koja je već kreirana
                sessionRef = db.collection("gameSessions")
                        .document(sessionId)
                        .collection("games")
                        .document("spojnice");

                if (isHost) {
                    // Host: sesija za spojnice još ne postoji — kreira je
                    // (KoZnaZna je koristila kolekciju "kzz", Spojnice trebaju svoju)
                    createSessionSilent();
                } else {
                    // Guest: čeka da host kreira spojnice sesiju, pa se pridružuje
                    tvInfo.setText("Čekam host da kreira Spojnice...");
                    waitForSessionThenJoin();
                }
            }
            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                tvInfo.setText("Greška: " + e.getMessage());
            }
        });
    }

    /** Kreira sesiju za Spojnice bez dijaloga (host, automatski). */
    private void createSessionSilent() {
        java.util.List<SpojniceQuestion> shuffled = new java.util.ArrayList<>(allQuestions);
        java.util.Collections.shuffle(shuffled);
        SpojniceQuestion q0 = shuffled.get(0);
        SpojniceQuestion q1 = shuffled.get(1);
        questions.clear();
        questions.add(q0);
        questions.add(q1);

        java.util.List<Integer> order1 = shuffledOrder();
        java.util.List<Integer> order2 = shuffledOrder();

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("phase",         PHASE_WAIT);
        data.put("p1Score",       0);
        data.put("p2Score",       0);
        data.put("connected",     buildFalseBoolList());
        data.put("connectedBy",   buildZeroIntList());
        data.put("connectedCount", 0);
        data.put("round1Order",   order1);
        data.put("round2Order",   order2);
        data.put("question0Id",   q0.docId != null ? q0.docId : "");
        data.put("question1Id",   q1.docId != null ? q1.docId : "");
        data.put("hostUid",       myUid);

        sessionRef.set(data)
                .addOnSuccessListener(v -> {
                    tvInfo.setText("Čekam Igrača 2... (Spojnice)");
                    startListening();
                })
                .addOnFailureListener(e -> tvInfo.setText("Greška: " + e.getMessage()));
    }

    /**
     * Guest čeka da se Spojnice sesija pojavi u Firestore (host je možda malo sporiji),
     * pa se automatski pridružuje.
     */
    private void waitForSessionThenJoin() {
        sessionRef.get().addOnSuccessListener(snap -> {
            if (!isAdded()) return;
            if (snap.exists()) {
                // Sesija postoji — pridruži se
                doJoinSession(snap);
            } else {
                // Još ne postoji — pokušaj ponovo za 1 sekundu
                tvInfo.setText("Čekam host... (pokušaj ponovo)");
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(this::waitForSessionThenJoin, 1000);
            }
        }).addOnFailureListener(e -> {
            if (isAdded()) tvInfo.setText("Greška spajanja: " + e.getMessage());
        });
    }

    /** Zajednička logika pridruživanja guesta sesiji (koristi i joinSession i waitForSessionThenJoin). */
    private void doJoinSession(com.google.firebase.firestore.DocumentSnapshot snap) {
        String q0Id = snap.getString("question0Id");
        String q1Id = snap.getString("question1Id");
        SpojniceQuestion q0 = findById(q0Id);
        SpojniceQuestion q1 = findById(q1Id);
        if (q0 == null || q1 == null) {
            tvInfo.setText("Greška: pitanja nisu nađena lokalno.");
            return;
        }
        questions.clear();
        questions.add(q0);
        questions.add(q1);
        sessionRef.update("phase", PHASE_R1_A, "guestUid", myUid)
                .addOnSuccessListener(v -> {
                    tvInfo.setText("Pridružen! Počinjemo...");
                    startListening();
                })
                .addOnFailureListener(e -> tvInfo.setText("Greška: " + e.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Dialog
    // ─────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────
    // Firestore session
    // ─────────────────────────────────────────────────────────────────────

    private void createSession() {
        sessionRef = db.collection("gameSessions")
                .document(sessionId)
                .collection("games")
                .document("spojnice");

        // Host bira 2 pitanja i upisuje njihove docId-ove
        List<SpojniceQuestion> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        SpojniceQuestion q0 = shuffled.get(0);
        SpojniceQuestion q1 = shuffled.get(1);

        // Postavi lokalna questions za hosta
        questions.clear();
        questions.add(q0);
        questions.add(q1);

        // Generiši shuffle redosled za prikaz desne kolone
        List<Integer> order1 = shuffledOrder();
        List<Integer> order2 = shuffledOrder();

        Map<String, Object> data = new HashMap<>();
        data.put("phase",         PHASE_WAIT);
        data.put("p1Score",       0);
        data.put("p2Score",       0);
        // Piši kao listu (Array) — NE dot-notation!
        data.put("connected",     buildFalseBoolList());
        data.put("connectedBy",   buildZeroIntList());
        data.put("connectedCount", 0);
        data.put("round1Order",   order1);
        data.put("round2Order",   order2);
        // Ključ za sinhronizaciju tema
        data.put("question0Id",   q0.docId != null ? q0.docId : "");
        data.put("question1Id",   q1.docId != null ? q1.docId : "");
        data.put("hostUid",       myUid);

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

        // Guest čita sesiju da dobije questionIds i orders
        sessionRef.get().addOnSuccessListener(snap -> {
            if (!isAdded()) return;
            if (!snap.exists()) {
                tvInfo.setText("Sesija nije nađena. Provjeri kod!");
                return;
            }
            doJoinSession(snap);
        }).addOnFailureListener(e -> tvInfo.setText("Sesija nije nađena. Provjeri kod!"));
    }

    private SpojniceQuestion findById(String docId) {
        if (docId == null) return null;
        for (SpojniceQuestion q : allQuestions) {
            if (docId.equals(q.docId)) return q;
        }
        return null;
    }

    private List<Integer> shuffledOrder() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) list.add(i);
        Collections.shuffle(list);
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Realtime listener
    // ─────────────────────────────────────────────────────────────────────

    private void startListening() {
        sessionListener = sessionRef.addSnapshotListener((snap, err) -> {
            if (!isAdded() || snap == null || !snap.exists()) return;

            String newPhase = snap.getString("phase");
            if (newPhase == null) return;

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

            // Učitaj connected state iz Firestore (uvijek, nije samo pri promjeni faze)
            List<?> connList = (List<?>) snap.get("connected");
            List<?> byList   = (List<?>) snap.get("connectedBy");
            int     cCount   = toInt(snap.get("connectedCount"));

            boolean[] newConnected   = new boolean[5];
            int[]     newConnectedBy = new int[5];

            if (connList != null && connList.size() == 5
                    && byList != null && byList.size() == 5) {
                for (int i = 0; i < 5; i++) {
                    newConnected[i]   = Boolean.TRUE.equals(connList.get(i));
                    newConnectedBy[i] = toInt(byList.get(i));
                }
            }

            if (!newPhase.equals(currentPhaseStr)) {
                // Nova faza
                currentPhaseStr = newPhase;
                connectedCount  = cCount;
                connected       = newConnected;
                connectedBy     = newConnectedBy;

                // Učitaj rightDisplayOrder za ovu rundu
                String orderKey = newPhase.startsWith("r1") ? "round1Order" : "round2Order";
                List<?> orderList = (List<?>) snap.get(orderKey);
                if (orderList != null && orderList.size() == 5) {
                    for (int i = 0; i < 5; i++) rightDisplayOrder[i] = toInt(orderList.get(i));
                }

                applyPhase(newPhase);
            } else {
                // Ista faza — provjeri jesu li se nova spajanja desila
                boolean changed = false;
                for (int i = 0; i < 5; i++) {
                    if (!connected[i] && newConnected[i]) {
                        // Netko je upravo spojio par i
                        connected[i]   = true;
                        connectedBy[i] = newConnectedBy[i];
                        renderConnectedPair(i, newConnectedBy[i]);
                        if (newConnectedBy[i] == 1) roundScore1 += 2;
                        else if (newConnectedBy[i] == 2) roundScore2 += 2;
                        changed = true;
                    }
                }
                if (changed) {
                    connectedCount = cCount;
                    updateScoreViews();
                    // Ako su svi parovi spojeni, odmah predje na sledecu fazu
                    // Ovo rjesava slucaj kada guest spoji posljednji par:
                    // host prima ovaj snapshot i on poziva advancePhase
                    if (connectedCount == 5 && !roundFinished) {
                        if (timer != null) timer.cancel();
                        tvTimer.setText("00:00");
                        if (soloMode || isHost) {
                            advancePhase(true);
                        } else {
                            roundFinished = true;
                            tvInfo.setText("Runda završena...");
                            setAllButtonsEnabled(false);
                        }
                    }
                }
                updateHeader();
                updateScoreViews();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Primijeni fazu
    // ─────────────────────────────────────────────────────────────────────

    private void applyPhase(String ph) {
        if (timer != null) timer.cancel();
        roundFinished = false;
        selectedLeft  = -1;
        for (int i = 0; i < 5; i++) attempted[i] = false;

        round = ph.startsWith("r1") ? 1 : 2;

        if (PHASE_R1_A.equals(ph) || PHASE_R2_B.equals(ph)) {
            activePlayer = 1;
        } else {
            activePlayer = 2;
        }

        myTurn = soloMode
                || (isHost  && activePlayer == 1)
                || (!isHost && activePlayer == 2);

        // Resetuj round score samo pri početku runde (Phase A)
        if (PHASE_R1_A.equals(ph) || PHASE_R2_A.equals(ph)) {
            roundScore1    = 0;
            roundScore2    = 0;
            connectedCount = 0;
            for (int i = 0; i < 5; i++) { connected[i] = false; connectedBy[i] = 0; }
        } else {
            // Phase B — rekonstruiši roundScore iz connected stanja
            roundScore1 = 0;
            roundScore2 = 0;
            for (int i = 0; i < 5; i++) {
                if (connected[i]) {
                    if (connectedBy[i] == 1) roundScore1 += 2;
                    else if (connectedBy[i] == 2) roundScore2 += 2;
                }
            }
        }

        setupRoundUI();
        updateHeader();
        updateScoreViews();
        startTimer(30);
    }

    private void setupRoundUI() {
        if (questions.isEmpty() || questions.size() < round) return;
        SpojniceQuestion q = questions.get(round - 1);
        tvCriteria.setText(q.criteria);

        // Lijeva kolona
        // Zamijeni postojeću petlju za lijevu kolonu u setupRoundUI:
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setText(q.leftItems.get(i));

            if (connected[i]) {
                renderConnectedPair(i, connectedBy[i]);
            } else if (attempted[i]) {
                // Pogrešno pokušan — prikaži crveno i onemogući
                leftButtons[i].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light));
                leftButtons[i].setEnabled(false);
                arrows[i].setText("✗");
                arrows[i].setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light));
            } else {
                leftButtons[i].setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.white));
                leftButtons[i].setEnabled(myTurn);
                arrows[i].setText("→");
                arrows[i].setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_blue));
            }
        }

        // Desna kolona — prema rightDisplayOrder (isti na oba telefona)
        for (int pos = 0; pos < 5; pos++) {
            rightButtons[pos].setText(q.rightItems.get(rightDisplayOrder[pos]));
        }

        // Desna dugmad
        for (int pos = 0; pos < 5; pos++) {
            boolean used = false;
            for (int i = 0; i < 5; i++) {
                if (connected[i] && q.correctPairs[i] == rightDisplayOrder[pos]) {
                    used = true;
                    break;
                }
            }
            rightButtons[pos].setEnabled(!used && myTurn);
            rightButtons[pos].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(),
                            used ? android.R.color.holo_green_dark : R.color.petal));
        }

        // Phase B — lijeva dugmad: samo nespojena su aktivna
        if (PHASE_R1_B.equals(currentPhaseStr) || PHASE_R2_B.equals(currentPhaseStr)) {
            for (int i = 0; i < 5; i++) {
                leftButtons[i].setEnabled(!connected[i] && !attempted[i] && myTurn);
            }
        }

        tvInfo.setText(myTurn
                ? "🎯 Tvoj red! Klikni lijevo → desno."
                : "⏳ Čekam protivnika...");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Klikovi
    // ─────────────────────────────────────────────────────────────────────

    private void onLeftClick(int index) {
        if (!myTurn || roundFinished) return;
        if (connected[index] || attempted[index]) return; // zaključan — spojen ili već pokušan

        // Deselekt istog
        if (selectedLeft == index) {
            leftButtons[index].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.white));
            selectedLeft = -1;
            tvInfo.setText("🎯 Tvoj red! Klikni lijevo → desno.");
            return;
        }

        // Deselektuj prethodni ako postoji
        if (selectedLeft != -1 && !connected[selectedLeft] && !attempted[selectedLeft]) {
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

        // Provjeri da desni pojam nije već spojen
        for (int i = 0; i < 5; i++) {
            if (connected[i] && q.correctPairs[i] == originalRightIndex) {
                Toast.makeText(requireContext(), "Ovaj pojam je već spojen!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        int pairIndex = selectedLeft;
        selectedLeft = -1; // uvijek resetuj odmah

        if (q.correctPairs[pairIndex] == originalRightIndex) {
            // ✓ Tačno — spoji
            connected[pairIndex]   = true;
            connectedBy[pairIndex] = activePlayer;
            connectedCount++;

            if (activePlayer == 1) roundScore1 += 2;
            else                   roundScore2 += 2;

            renderConnectedPair(pairIndex, activePlayer);
            updateScoreViews();
            saveConnectionToFirestore(pairIndex);

        } else {
            // ✗ Netačno — zaključaj taj lijevi pojam zauvijek
            attempted[pairIndex] = true;
            leftButtons[pairIndex].setBackgroundTintList(
                    ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light));
            leftButtons[pairIndex].setEnabled(false);
            tvInfo.setText("❌ Netačno! Taj pojam više nije dostupan.");
        }
    }

    /**
     * Snima spajanje para u Firestore.
     *
     * KRITIČNO: Firestore NE podržava dot-notation za Array elemente!
     * Ako napišemo update.put("connected.0", true) — to NEĆE raditi za arraye,
     * samo za Map polja. Moramo uvijek pisati CIJELU listu odjednom.
     */
    private void saveConnectionToFirestore(int pairIndex) {
        if (soloMode) {
            if (connectedCount == 5) onAllConnected();
            return;
        }

        // Izgradi nove liste (kopija lokalnog stanja)
        List<Boolean> newConnected   = new ArrayList<>();
        List<Integer> newConnectedBy = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            newConnected.add(connected[i]);
            newConnectedBy.add(connectedBy[i]);
        }

        String scoreField = (activePlayer == 1) ? "p1Score" : "p2Score";

        Map<String, Object> update = new HashMap<>();
        // UVIJEK piši CIJELU listu — ne dot-notation za array!
        update.put("connected",    newConnected);
        update.put("connectedBy",  newConnectedBy);
        update.put("connectedCount", connectedCount);
        update.put(scoreField, com.google.firebase.firestore.FieldValue.increment(2));

        sessionRef.update(update)
                .addOnSuccessListener(v -> {
                    if (!isAdded()) return;
                    if (connectedCount == 5) onAllConnected();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Greška spajanja: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void onAllConnected() {
        if (!roundFinished) advancePhase(true);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Prikaz spojenog para
    // ─────────────────────────────────────────────────────────────────────

    private void renderConnectedPair(int leftIndex, int byPlayer) {
        if (questions.isEmpty() || questions.size() < round) return;
        SpojniceQuestion q = questions.get(round - 1);

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

    // ─────────────────────────────────────────────────────────────────────
    // Timer
    // ─────────────────────────────────────────────────────────────────────

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer((long) seconds * 1000, 1000) {
            @Override
            public void onTick(long ms) {
                if (!isAdded()) return;
                tvTimer.setText(String.format("00:%02d", ms / 1000 + 1));
            }
            @Override
            public void onFinish() {
                if (!isAdded()) return;
                tvTimer.setText("00:00");
                onTimerExpired();
            }
        };
        timer.start();
    }

    private void onTimerExpired() {
        if (roundFinished) return;
        // Samo igrač na potezu (ili host kao arbitar) napreduje fazu
        if (!soloMode && !myTurn && !isHost) {
            tvInfo.setText("Čekam...");
            return;
        }
        advancePhase(false);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Napredovanje faze
    // ─────────────────────────────────────────────────────────────────────

    private void advancePhase(boolean allConnected) {
        if (timer != null) timer.cancel();
        roundFinished = true;

        int remaining = 0;
        for (boolean c : connected) if (!c) remaining++;

        String nextPhase;
        switch (currentPhaseStr) {
            case PHASE_R1_A:
                nextPhase = (allConnected || remaining == 0) ? PHASE_R2_A : PHASE_R1_B;
                break;
            case PHASE_R1_B:
                nextPhase = PHASE_R2_A;
                break;
            case PHASE_R2_A:
                nextPhase = (allConnected || remaining == 0) ? PHASE_DONE : PHASE_R2_B;
                break;
            default: // PHASE_R2_B
                nextPhase = PHASE_DONE;
                break;
        }

        String finalNextPhase = nextPhase;

        if (soloMode) {
            new CountDownTimer(2000, 1000) {
                @Override public void onTick(long ms) {
                    if (isAdded()) tvInfo.setText("Nastavak za: " + (ms / 1000 + 1) + "s...");
                }
                @Override public void onFinish() {
                    if (!isAdded()) return;
                    currentPhaseStr = finalNextPhase;
                    if (PHASE_DONE.equals(finalNextPhase)) { showEndGame(); return; }
                    if (PHASE_R2_A.equals(finalNextPhase)) {
                        connectedCount = 0;
                        for (int i = 0; i < 5; i++) { connected[i] = false; connectedBy[i] = 0; }
                        for (int i = 0; i < 5; i++) rightDisplayOrder[i] = savedRound2Order[i];
                    }
                    applyPhase(finalNextPhase);
                }
            }.start();
        } else {
            // Samo host mijenja fazu u Firestoru
            if (isHost) {
                Map<String, Object> update = new HashMap<>();
                update.put("phase", nextPhase);
                if (PHASE_R2_A.equals(nextPhase)) {
                    update.put("connected",    buildFalseBoolList());
                    update.put("connectedBy",  buildZeroIntList());
                    update.put("connectedCount", 0);
                }
                sessionRef.update(update)
                        .addOnFailureListener(e -> {
                            if (isAdded())
                                Toast.makeText(requireContext(), "Greška napredovanja: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                        });
            }
            tvInfo.setText("Runda završena...");
            setAllButtonsEnabled(false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Kraj igre
    // ─────────────────────────────────────────────────────────────────────

    private void showEndGame() {
        if (gameEnded) return;
        gameEnded = true;
        if (timer != null) timer.cancel();
        if (!isAdded()) return;

        int myScore     = isHost ? player1Score : player2Score;
        int myConnected = myScore / 2;
        try {
            new StatsRepository().saveSpojniceResult(myConnected, 10, myScore);
        } catch (Exception ignored) {}

        String result;
        if (player1Score > player2Score)
            result = "Pobjednik spojnica: Igrač 1! (" + player1Score + " vs " + player2Score + ")";
        else if (player2Score > player1Score)
            result = "Pobjednik spojnica: Igrač 2! (" + player2Score + " vs " + player1Score + ")";
        else
            result = "Spojnice izjednačene! (" + player1Score + " : " + player2Score + ")";

        Toast.makeText(requireContext(), result, Toast.LENGTH_LONG).show();
        try {
            Bundle args = new Bundle();
            args.putString("sessionId", sessionId);
            args.putBoolean("isHost", isHost);
            args.putString("hostUid", myUid);

            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_spojnice_to_associations, args);
        } catch (Exception e) {
            // Navigation fallback — ako akcija ne postoji, samo ostani
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Solo mod
    // ─────────────────────────────────────────────────────────────────────

    private void startSoloGame() {
        player1Score = 0;
        player2Score = 0;
        List<SpojniceQuestion> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);
        questions.clear();
        questions.add(shuffled.get(0));
        questions.add(shuffled.get(1));

        List<Integer> order1 = shuffledOrder();
        List<Integer> order2 = shuffledOrder();
        for (int i = 0; i < 5; i++) {
            rightDisplayOrder[i] = order1.get(i);
            savedRound2Order[i]  = order2.get(i);
        }
        currentPhaseStr = PHASE_R1_A;
        applyPhase(PHASE_R1_A);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

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
        tvScore.setText(player1Score + " : " + player2Score);
    }

    private void updateScoreViews() {

    }

    private void setAllButtonsEnabled(boolean enabled) {
        for (int i = 0; i < 5; i++) {
            leftButtons[i].setEnabled(enabled);
            rightButtons[i].setEnabled(enabled);
        }
    }

    private List<Boolean> buildFalseBoolList() {
        List<Boolean> l = new ArrayList<>();
        for (int i = 0; i < 5; i++) l.add(false);
        return l;
    }

    private List<Integer> buildZeroIntList() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < 5; i++) l.add(0);
        return l;
    }

    private int  toInt(Object v)  { return (int) toLong(v); }
    private long toLong(Object v) {
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Boolean) return Boolean.TRUE.equals(v) ? 1L : 0L;
        return 0L;
    }
}