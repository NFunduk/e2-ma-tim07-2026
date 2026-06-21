package com.example.sabona.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * Prima FCM push notifikacije.
 *
 * VAŽNO: Android sistem automatski pokreće ovaj servis (čak i ako je
 * aplikacija potpuno ugašena / proces ubijen) kada push poruka stigne
 * sa Firebase servera. Zato ovde NE proveravamo da li je korisnik
 * "online" — ako je push uopšte stigao do telefona, znači da app
 * trenutno nije u prvom planu (FCM ne šalje "notification" tip poruke
 * kroz ovaj servis dok je app aktivno otvorena na istom ekranu –
 * umesto toga, dok je app u prvom planu, in-app Firestore listener iz
 * MainActivity (startListeningForSystemNotifications) već prikazuje
 * obaveštenje, pa ne dupliramo).
 *
 * Token (FCM "adresa" ovog uređaja) se čuva u Firestore-u na
 * users/{uid}.fcmToken, kako bi Cloud Function znala kome da pošalje
 * push kad stigne nova poruka u čet (vidi functions/index.js).
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_SERVICE";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Novi FCM token: " + token);
        saveTokenToFirestore(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title;
        String body;
        String channel = "Ostalo";

        // Cloud Function šalje "data" payload (ne "notification" payload),
        // da bismo imali punu kontrolu nad izgledom i kanalom obaveštenja
        // i da bi onMessageReceived bio pozvan i kad je app u pozadini.
        Map<String, String> data = remoteMessage.getData();
        if (!data.isEmpty()) {
            title = data.getOrDefault("title", "SaBoNa");
            body = data.getOrDefault("body", "");
            channel = data.getOrDefault("channel", "Ostalo");
        } else if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        } else {
            return;
        }

        com.example.sabona.model.AppNotification notification =
                new com.example.sabona.model.AppNotification(
                        title, body, channel,
                        "chat", null,
                        false, false
                );

        NotificationHelper.showNotification(getApplicationContext(), notification);
    }

    /** Upisuje (ili osvežava) FCM token ulogovanog korisnika u Firestore. */
    public static void saveTokenToFirestore(String token) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Korisnik trenutno nije ulogovan – token će se sačuvati
            // naknadno, odmah nakon uspešnog logina (vidi LoginFragment / AuthRepository).
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("fcmToken", token);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(data, SetOptions.merge());
    }
}