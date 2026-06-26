package com.example.sabona.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Ažurira "online" i "inGame" status ulogovanog igrača u Firestore-u
 * (users/{uid}.online, users/{uid}.inGame), kako bi prijatelji mogli
 * vidjeti da li je igrač trenutno dostupan za partiju.
 *
 * "online" se postavlja na true kada je aplikacija u prvom planu
 * (Activity onResume) i na false kada ode u pozadinu (onPause) ili
 * kada se korisnik odjavi.
 *
 * "inGame" se postavlja na true dok je igrač na nekom od ekrana sa
 * igrom (gameDestinations u MainActivity), i na false kada izađe.
 */
public class PresenceManager {

    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private PresenceManager() {}

    private static String currentUid() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    public static void setOnline(boolean online) {
        String uid = currentUid();
        if (uid == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("online", online);
        // Firestore .update() puca ako dokument ne postoji (npr. odmah nakon registracije),
        // pa koristimo merge-set da bude bezbedno u svim slučajevima.
        db.collection("users").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    public static void setInGame(boolean inGame) {
        String uid = currentUid();
        if (uid == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("inGame", inGame);
        db.collection("users").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    public static void setInChat(boolean inChat) {
        String uid = currentUid();
        if (uid == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("inChat", inChat);
        db.collection("users").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }
}
