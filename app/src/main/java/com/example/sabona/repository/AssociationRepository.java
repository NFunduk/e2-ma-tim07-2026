package com.example.sabona.repository;

import com.example.sabona.model.AssociationGame;
import com.google.firebase.firestore.FirebaseFirestore;

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
                    List<AssociationGame> games =
                            snapshot.toObjects(AssociationGame.class);
                    callback.onSuccess(games);
                })
                .addOnFailureListener(callback::onError);
    }
}