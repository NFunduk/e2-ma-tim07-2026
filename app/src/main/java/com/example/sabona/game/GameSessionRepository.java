package com.example.sabona.game;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class GameSessionRepository {

    private static final String COL_GAMES    = "games";
    private static final String DOC_KORAK    = "korakPoKorak";
    private static final String DOC_MOJ_BROJ = "mojBroj";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── Korak po korak ───────────────────────────────────────────────

    private DocumentReference korakRef() {
        return db.collection(COL_GAMES)
                .document(GameSessionManager.GAME_ID)
                .collection("games")
                .document(DOC_KORAK);
    }

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

    // ── Moj broj ─────────────────────────────────────────────────────

    private DocumentReference mojBrojRef() {
        return db.collection(COL_GAMES)
                .document(GameSessionManager.GAME_ID)
                .collection("games")
                .document(DOC_MOJ_BROJ);
    }

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
}