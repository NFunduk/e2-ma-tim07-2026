package com.example.sabona.repository;

import com.example.sabona.model.AssociationGame;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AssociationRepository {

    public interface Callback {
        void onSuccess(List<AssociationGame> games);
        void onError(Exception e);
    }

    public void getAssociations(Callback callback) {
        FirebaseFirestore.getInstance()
                .collection("associations")
                .orderBy("order")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AssociationGame> games = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        AssociationGame game = doc.toObject(AssociationGame.class);

                        if (game != null) {
                            game.docId = doc.getId();
                            games.add(game);
                        }
                    }

                    callback.onSuccess(games);
                })
                .addOnFailureListener(callback::onError);
    }
}