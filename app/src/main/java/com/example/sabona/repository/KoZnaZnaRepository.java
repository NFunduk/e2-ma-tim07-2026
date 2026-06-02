package com.example.sabona.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class KoZnaZnaRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface QuestionsCallback {
        void onSuccess(List<Question> questions);
        void onError(Exception e);
    }

    public void fetchQuestions(QuestionsCallback callback) {
        db.collection("questions_ko_zna_zna")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Question> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        try {
                            String questionText = doc.getString("question");
                            List<String> answers = (List<String>) doc.get("answers");
                            // Firestore čuva kao int64, pa castamo na Long pa int
                            Long correctIndexLong = doc.getLong("correctIndex");
                            int correctIndex = correctIndexLong != null ? correctIndexLong.intValue() : 0;
                            String category = doc.getString("category");

                            if (questionText != null && answers != null && answers.size() == 4) {
                                list.add(new Question(questionText, answers, correctIndex, category));
                            }
                        } catch (Exception e) {
                            // Preskoči loš dokument
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(callback::onError);
    }

    // Model klasa
    public static class Question {
        public final String question;
        public final List<String> answers;
        public final int correctIndex;
        public final String category;

        public Question(String question, List<String> answers, int correctIndex, String category) {
            this.question = question;
            this.answers = answers;
            this.correctIndex = correctIndex;
            this.category = category;
        }
    }
}