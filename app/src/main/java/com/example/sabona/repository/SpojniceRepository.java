package com.example.sabona.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class SpojniceRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── Stari callback (kompatibilnost) ────────────────────────────────────
    public interface SpojniceCallback {
        void onSuccess(List<SpojniceQuestion> questions);
        void onError(Exception e);
    }

    // ── Novi callback sa docId ─────────────────────────────────────────────
    public interface SpojniceWithIdsCallback {
        void onSuccess(List<SpojniceQuestion> questions);
        void onError(Exception e);
    }

    public void fetchQuestions(SpojniceCallback callback) {
        fetchQuestionsWithIds(new SpojniceWithIdsCallback() {
            @Override public void onSuccess(List<SpojniceQuestion> q) { callback.onSuccess(q); }
            @Override public void onError(Exception e)                { callback.onError(e); }
        });
    }

    public void fetchQuestionsWithIds(SpojniceWithIdsCallback callback) {
        db.collection("questions_spojnice")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<SpojniceQuestion> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        try {
                            String criteria     = doc.getString("criteria");
                            List<String> leftItems  = (List<String>) doc.get("leftItems");
                            List<String> rightItems = (List<String>) doc.get("rightItems");
                            List<Object> pairsRaw   = (List<Object>) doc.get("correctPairs");

                            if (criteria == null || leftItems == null
                                    || rightItems == null || pairsRaw == null) continue;
                            if (leftItems.size() != 5 || rightItems.size() != 5
                                    || pairsRaw.size() != 5) continue;

                            int[] correctPairs = new int[5];
                            for (int i = 0; i < 5; i++) {
                                Object val = pairsRaw.get(i);
                                if (val instanceof Long)         correctPairs[i] = ((Long) val).intValue();
                                else if (val instanceof Integer) correctPairs[i] = (Integer) val;
                                else                             correctPairs[i] = Integer.parseInt(String.valueOf(val));
                            }

                            // Sačuvaj docId — ključan za sinhronizaciju u multiplayeru
                            list.add(new SpojniceQuestion(doc.getId(), criteria, leftItems, rightItems, correctPairs));

                        } catch (Exception e) {
                            // Preskoči loš dokument
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    // ── Model klasa ────────────────────────────────────────────────────────
    public static class SpojniceQuestion {
        public final String docId;          // Firestore document ID — za sinhronizaciju
        public final String criteria;
        public final List<String> leftItems;
        public final List<String> rightItems;
        public final int[] correctPairs;    // correctPairs[i] = index u rightItems za leftItems[i]

        /** Konstruktor SA docId (novi) */
        public SpojniceQuestion(String docId, String criteria,
                                List<String> leftItems, List<String> rightItems,
                                int[] correctPairs) {
            this.docId        = docId;
            this.criteria     = criteria;
            this.leftItems    = leftItems;
            this.rightItems   = rightItems;
            this.correctPairs = correctPairs;
        }

        /** Konstruktor BEZ docId (kompatibilnost) */
        public SpojniceQuestion(String criteria,
                                List<String> leftItems, List<String> rightItems,
                                int[] correctPairs) {
            this(null, criteria, leftItems, rightItems, correctPairs);
        }
    }
}
