package com.example.sabona.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class SpojniceRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface SpojniceCallback {
        void onSuccess(List<SpojniceQuestion> questions);
        void onError(Exception e);
    }

    public void fetchQuestions(SpojniceCallback callback) {
        db.collection("questions_spojnice")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<SpojniceQuestion> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        try {
                            String criteria     = doc.getString("criteria");
                            List<String> leftItems  = (List<String>) doc.get("leftItems");
                            List<String> rightItems = (List<String>) doc.get("rightItems");
                            List<Object> pairsRaw = (List<Object>) doc.get("correctPairs");

                            if (criteria == null || leftItems == null
                                    || rightItems == null || pairsRaw == null) continue;
                            if (leftItems.size() != 5 || rightItems.size() != 5
                                    || pairsRaw.size() != 5) continue;

                            // Radi i ako su u Firestore-u uneseni kao string ("0") ili number (0)
                            int[] correctPairs = new int[5];
                            for (int i = 0; i < 5; i++) {
                                Object val = pairsRaw.get(i);
                                if (val instanceof Long) {
                                    correctPairs[i] = ((Long) val).intValue();
                                } else if (val instanceof String) {
                                    correctPairs[i] = Integer.parseInt((String) val);
                                } else {
                                    correctPairs[i] = Integer.parseInt(String.valueOf(val));
                                }
                            }

                            list.add(new SpojniceQuestion(criteria, leftItems, rightItems, correctPairs));

                        } catch (Exception e) {
                            // Preskoči loš dokument
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    // ---- Model klasa ----
    public static class SpojniceQuestion {
        public final String criteria;
        public final List<String> leftItems;
        public final List<String> rightItems;
        public final int[] correctPairs; // correctPairs[i] = index u rightItems koji odgovara leftItems[i]

        public SpojniceQuestion(String criteria,
                                List<String> leftItems,
                                List<String> rightItems,
                                int[] correctPairs) {
            this.criteria     = criteria;
            this.leftItems    = leftItems;
            this.rightItems   = rightItems;
            this.correctPairs = correctPairs;
        }
    }
}