package com.example.sabona.repository;

import com.example.sabona.model.KorakGame;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class KorakRepository {

    public interface Callback {
        void onSuccess(List<KorakGame> games);
        void onError(Exception e);
    }

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