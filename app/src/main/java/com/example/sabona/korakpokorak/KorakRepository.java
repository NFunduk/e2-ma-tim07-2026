package com.example.sabona.korakpokorak;

import com.example.sabona.model.KorakGame;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Repozitorijum za Korak po Korak igre.
 *
 * Konzistentno s KoZnaZnaRepository — fetchQuestionsWithIds vraća docId
 * uz svaki objekat, da bi host i guest mogli sinhronizovati iste igre.
 */
public class KorakRepository {

    /** Stari callback (bez docId) — ostaje radi kompatibilnosti */
    public interface Callback {
        void onSuccess(List<KorakGame> games);
        void onError(Exception e);
    }

    /** Novi callback s docId — koristi KorakViewModel */
    public interface CallbackWithIds {
        void onSuccess(List<KorakGame> games);
        void onError(Exception e);
    }

    /** Učitava sve igre sa docId popunjenim u KorakGame.docId */
    public void getGamesWithIds(CallbackWithIds callback) {
        FirebaseFirestore.getInstance()
                .collection("korak_po_korak")
                .orderBy("order")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<KorakGame> games = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        KorakGame game = doc.toObject(KorakGame.class);
                        if (game != null) {
                            game.docId = doc.getId(); // ključno za sinhronizaciju
                            games.add(game);
                        }
                    }
                    callback.onSuccess(games);
                })
                .addOnFailureListener(callback::onError);
    }

    /** Stara metoda bez docId */
    public void getGames(Callback callback) {
        FirebaseFirestore.getInstance()
                .collection("korak_po_korak")
                .orderBy("order")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<KorakGame> games = snapshot.toObjects(KorakGame.class);
                    callback.onSuccess(games);
                })
                .addOnFailureListener(callback::onError);
    }
}