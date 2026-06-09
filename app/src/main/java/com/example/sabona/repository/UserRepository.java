package com.example.sabona.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public interface UserCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    /** Učitaj podatke ulogovanog igrača */
    public void getCurrentUser(UserCallback<Map<String, Object>> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("Nije prijavljen nijedan korisnik");
            return;
        }
        db.collection("users").document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onSuccess(doc.getData());
                    } else {
                        callback.onError("Korisnik nije pronađen u bazi");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Greška pri učitavanju profila"));
    }

    /** Sačuvaj odabrani avatar */
    public void updateAvatar(String avatarResName, UserCallback<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { callback.onError("Nije prijavljen"); return; }

        db.collection("users").document(user.getUid())
                .update("avatarRes", avatarResName)
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError("Greška pri izmjeni avatara"));
    }

    /** Učitaj statistiku igrača */
    public void getStats(UserCallback<Map<String, Object>> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { callback.onError("Nije prijavljen"); return; }

        db.collection("stats").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onSuccess(doc.getData());
                    } else {
                        // Nema statistike – vrati praznu mapu
                        callback.onSuccess(new HashMap<>());
                    }
                })
                .addOnFailureListener(e -> callback.onError("Greška pri učitavanju statistike"));
    }
}
