package com.example.sabona.game;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.example.sabona.game.SkockoGameState;
import com.example.sabona.game.AsocijacijeGameState;

import java.util.function.Function;

/**
 * Pristup Firestoru za igre.
 *
 * Struktura (ista kao Ko Zna Zna):
 *   gameSessions/{sessionId}/games/korakPoKorak
 *   gameSessions/{sessionId}/games/mojBroj
 */
public class GameSessionRepository {

    private static final String DOC_KORAK    = "korakPoKorak";
    private static final String DOC_MOJ_BROJ = "mojBroj";

    private static final String DOC_SKOCKO = "skocko";

    private static final String DOC_ASOCIJACIJE = "asocijacije";

    private final FirebaseFirestore   db         = FirebaseFirestore.getInstance();
    private final GameSessionManager  sessionMgr = GameSessionManager.get();

    // ── Document refs ────────────────────────────────────────────────

    private DocumentReference korakRef() {
        return db.collection(GameSessionManager.COL_GAME_SESSIONS)
                .document(sessionMgr.getSessionId())
                .collection("games")
                .document(DOC_KORAK);
    }

    private DocumentReference mojBrojRef() {
        return db.collection(GameSessionManager.COL_GAME_SESSIONS)
                .document(sessionMgr.getSessionId())
                .collection("games")
                .document(DOC_MOJ_BROJ);
    }

    private DocumentReference skockoRef() {
        return db.collection(GameSessionManager.COL_GAME_SESSIONS)
                .document(sessionMgr.getSessionId())
                .collection("games")
                .document(DOC_SKOCKO);
    }

    private DocumentReference asocijacijeRef() {
        return db.collection(GameSessionManager.COL_GAME_SESSIONS)
                .document(sessionMgr.getSessionId())
                .collection("games")
                .document(DOC_ASOCIJACIJE);
    }

    // ── Korak po korak ───────────────────────────────────────────────

    public void initKorakState(KorakGameState state) {
        korakRef().set(state);
    }

    public void updateKorakState(KorakGameState state) {
        state.updatedAt = Timestamp.now();
        korakRef().set(state);
    }

    public ListenerRegistration listenKorak(EventListener<DocumentSnapshot> listener) {
        return korakRef().addSnapshotListener(listener);
    }

    /**
     * Transakciono ažuriranje Korak stanja — čita najnoviju verziju sa servera
     * pre mutacije, čime se sprečava da dva istovremena pisanja jedno pregazi drugo
     * (npr. host i guest istovremeno mijenjaju stanje na osnovu zastarjelog lokalnog kopija).
     *
     * @param updater funkcija koja prima trenutno (sa servera) stanje i vraća novo stanje za upis,
     *                ili null ako ne treba ništa upisati (npr. uslov vise nije ispunjen).
     */
    public void runKorakTransaction(Function<KorakGameState, KorakGameState> updater) {
        FirebaseFirestore.getInstance().runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(korakRef());
            KorakGameState current = snap.toObject(KorakGameState.class);
            if (current == null) current = new KorakGameState();
            KorakGameState updated = updater.apply(current);
            if (updated != null) {
                updated.updatedAt = Timestamp.now();
                transaction.set(korakRef(), updated);
            }
            return null;
        });
    }

    /** Guest čita sesiju da bi dobio hostUid */
    public void getKorakOnce(EventListener<DocumentSnapshot> listener) {
        korakRef().get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                listener.onEvent(task.getResult(), null);
            }
        });
    }

    // ── Moj broj ─────────────────────────────────────────────────────

    public void initMojBrojState(MojBrojGameState state) {
        mojBrojRef().set(state);
    }

    public void updateMojBrojState(MojBrojGameState state) {
        state.updatedAt = Timestamp.now();
        mojBrojRef().set(state);
    }

    public ListenerRegistration listenMojBroj(EventListener<DocumentSnapshot> listener) {
        return mojBrojRef().addSnapshotListener(listener);
    }

    /**
     * Transakciono ažuriranje Moj Broj stanja — vidi {@link #runKorakTransaction}.
     * Koristi se za sve akcije gde oba igrača mogu pisati skoro istovremeno
     * (npr. predaja izraza oba igrača, prelazak na sledeću rundu).
     */
    public void runMojBrojTransaction(Function<MojBrojGameState, MojBrojGameState> updater) {
        FirebaseFirestore.getInstance().runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(mojBrojRef());
            MojBrojGameState current = snap.toObject(MojBrojGameState.class);
            if (current == null) current = new MojBrojGameState();
            MojBrojGameState updated = updater.apply(current);
            if (updated != null) {
                updated.updatedAt = Timestamp.now();
                transaction.set(mojBrojRef(), updated);
            }
            return null;
        });
    }

    /** Ažurira samo status sesije (za označavanje kao "waiting_p2" → "playing") */
    public void updateSessionStatus(String status) {
        db.collection(GameSessionManager.COL_GAME_SESSIONS)
                .document(sessionMgr.getSessionId())
                .update("status", status);
    }

    //Skocko

    public void initSkockoState(SkockoGameState state) {
        skockoRef().set(state);
    }

    public void updateSkockoState(SkockoGameState state) {
        state.updatedAt = Timestamp.now();
        skockoRef().set(state);
    }

    public ListenerRegistration listenSkocko(EventListener<DocumentSnapshot> listener) {
        return skockoRef().addSnapshotListener(listener);
    }

    // Asocijacije
    public void initAsocijacijeState(AsocijacijeGameState state) {
        asocijacijeRef().set(state);
    }

    public void updateAsocijacijeState(AsocijacijeGameState state) {
        state.updatedAt = Timestamp.now();
        asocijacijeRef().set(state);
    }

    public ListenerRegistration listenAsocijacije(EventListener<DocumentSnapshot> listener) {
        return asocijacijeRef().addSnapshotListener(listener);
    }
}