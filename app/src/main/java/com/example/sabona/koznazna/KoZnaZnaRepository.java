package com.example.sabona.koznazna;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class KoZnaZnaRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── Stari callback (ostavljamo za kompatibilnost) ───────────────────────
    public interface QuestionsCallback {
        void onSuccess(List<Question> questions);
        void onError(Exception e);
    }

    // ── Novi callback koji vraća pitanja SA docId-ovima ────────────────────
    public interface QuestionsWithIdsCallback {
        void onSuccess(List<Question> questions);
        void onError(Exception e);
    }

    /** Učitava pitanja bez čuvanja docId (stara metoda, za solo/kompatibilnost) */
    public void fetchQuestions(QuestionsCallback callback) {
        fetchQuestionsWithIds(new QuestionsWithIdsCallback() {
            @Override public void onSuccess(List<Question> questions) { callback.onSuccess(questions); }
            @Override public void onError(Exception e)                { callback.onError(e); }
        });
    }

    /** Učitava pitanja sa docId — potrebno za sinhronizaciju u multiplayeru */
    public void fetchQuestionsWithIds(QuestionsWithIdsCallback callback) {
        db.collection("questions_ko_zna_zna")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Question> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        try {
                            String questionText     = doc.getString("question");
                            List<String> answers    = (List<String>) doc.get("answers");
                            Long correctIndexLong   = doc.getLong("correctIndex");
                            int  correctIndex       = correctIndexLong != null ? correctIndexLong.intValue() : 0;
                            String category         = doc.getString("category");

                            if (questionText != null && answers != null && answers.size() == 4) {
                                // doc.getId() — document ID iz Firestore-a (ključ za sinhronizaciju)
                                list.add(new Question(doc.getId(), questionText, answers, correctIndex, category));
                            }
                        } catch (Exception e) {
                            // Preskoči loš dokument
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    // ── Model klasa ────────────────────────────────────────────────────────
    public static class Question {
        public final String docId;       // Firestore document ID — ključ za sinhronizaciju
        public final String question;
        public final List<String> answers;
        public final int correctIndex;
        public final String category;

        /** Konstruktor SA docId (novi, preporučen) */
        public Question(String docId, String question, List<String> answers,
                        int correctIndex, String category) {
            this.docId        = docId;
            this.question     = question;
            this.answers      = answers;
            this.correctIndex = correctIndex;
            this.category     = category;
        }

        /** Konstruktor BEZ docId (za kompatibilnost sa starim kodom) */
        public Question(String question, List<String> answers,
                        int correctIndex, String category) {
            this(null, question, answers, correctIndex, category);
        }
    }
}
