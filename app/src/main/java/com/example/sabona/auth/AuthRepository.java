package com.example.sabona.auth;

import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AuthRepository {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public void register(String email, String username, String password, String region, Callback callback) {

        // Proveri da username nije zauzet
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        callback.onError("Korisničko ime je zauzeto");
                        return;
                    }
                    //Kreiraj Firebase Auth nalog
                    auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener(result -> {
                                FirebaseUser user = result.getUser();
                                if (user == null) {
                                    callback.onError("Greška pri kreiranju naloga");
                                    return;
                                }
                                // Pošalji verifikacioni email
                                user.sendEmailVerification();
                                // Sačuvaj u Firestore
                                saveUserToFirestore(user.getUid(), email, username, region, callback);
                            })
                            .addOnFailureListener(e -> callback.onError(parseError(e)));
                })
                .addOnFailureListener(e ->
                        callback.onError("Greška pri proveri korisničkog imena"));
    }

    private void saveUserToFirestore(String uid, String email, String username, String region, Callback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("email", email);
        data.put("username", username);
        data.put("region", region);
        data.put("tokens", 5);
        data.put("stars", 0);
        data.put("league", 0);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .set(data)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    // Ako Firestore padne, obriši i Auth nalog da ne ostane siročić
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) user.delete();
                    callback.onError("Greška pri čuvanju podataka. Pokušaj ponovo.");
                });
    }

    public void login(String identifier, String password, Callback callback) {
        if (identifier.contains("@")) {
            signIn(identifier, password, callback);
        } else {
            db.collection("users")
                    .whereEqualTo("username", identifier)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap.isEmpty()) {
                            callback.onError("Korisnik nije pronađen");
                            return;
                        }
                        String email = snap.getDocuments().get(0).getString("email");
                        signIn(email, password, callback);
                    })
                    .addOnFailureListener(e -> callback.onError("Greška pri prijavi"));
        }
    }

    private void signIn(String email, String password, Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    // logovanje tek nakon potvrde mejla
                    if (user != null && !user.isEmailVerified()) {
                        auth.signOut();
                        callback.onError(
                                "Email nije potvrđen. Klikni na link koji si dobio/la.");
                        return;
                    }
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(parseError(e)));
    }

    public void checkVerification(Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Nema prijavljenog korisnika");
            return;
        }

         user.reload()
                .addOnSuccessListener(unused -> {
                    FirebaseUser refreshed = auth.getCurrentUser();
                    if (refreshed != null && refreshed.isEmailVerified()) {
                        callback.onSuccess();
                    } else {
                        callback.onError("Email još nije potvrđen. Proveri inbox (i spam).");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Greška pri proveri. Pokušaj ponovo."));
    }

    public void resendVerification(Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Nema prijavljenog korisnika");
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Greška pri slanju emaila"));
    }

    public void changePassword(String oldPassword, String newPassword, Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            callback.onError("Korisnik nije prijavljen");
            return;
        }

        auth.signInWithEmailAndPassword(user.getEmail(), oldPassword)
                .addOnSuccessListener(result -> {
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(unused -> callback.onSuccess())
                            .addOnFailureListener(e ->
                                    callback.onError("Greška pri promeni lozinke"));
                })
                .addOnFailureListener(e ->
                        callback.onError("Stara lozinka nije ispravna"));
    }

   public void logout() {
        auth.signOut();
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    private String parseError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "Nepoznata greška";
        if (msg.contains("email address is already in use"))
            return "Email je već registrovan";
        if (msg.contains("badly formatted"))
            return "Neispravna email adresa";
        if (msg.contains("INVALID_LOGIN_CREDENTIALS")
                || msg.contains("password is invalid")
                || msg.contains("no user record"))
            return "Pogrešan email/korisničko ime ili lozinka";
        if (msg.contains("network error") || msg.contains("NETWORK"))
            return "Problem sa internet konekcijom";
        if (msg.contains("too many requests") || msg.contains("TOO_MANY_ATTEMPTS"))
            return "Previše pokušaja. Sačekaj malo pa pokušaj ponovo.";
        return "Greška: " + msg;
    }
}