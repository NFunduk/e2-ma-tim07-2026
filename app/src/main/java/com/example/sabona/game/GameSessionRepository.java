package com.example.sabona.game;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

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

    /** Ažurira samo status sesije (za označavanje kao "waiting_p2" → "playing") */
    public void updateSessionStatus(String status) {
        db.collection(GameSessionManager.COL_GAME_SESSIONS)
                .document(sessionMgr.getSessionId())
                .update("status", status);
    }
}