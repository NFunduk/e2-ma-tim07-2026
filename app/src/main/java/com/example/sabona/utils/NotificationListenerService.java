package com.example.sabona.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.sabona.R;
import com.example.sabona.model.AppNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

public class NotificationListenerService extends Service {

    private static final String FOREGROUND_CHANNEL_ID = "foreground_service";
    private static final int FOREGROUND_NOTIF_ID = 9999;

    private ListenerRegistration listener;
    private boolean firstLoad = true;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotification());
        startListening();
        // START_STICKY: ako Android ubije servis zbog memorije, ponovo ga pokreće automatski
        return START_STICKY;
    }

    private void startListening() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            stopSelf();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        listener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        if (change.getType() == DocumentChange.Type.ADDED) {
                            AppNotification notification =
                                    change.getDocument().toObject(AppNotification.class);
                            notification.setId(change.getDocument().getId());

                            if (!firstLoad && !notification.isRead()) {
                                NotificationHelper.showNotification(
                                        getApplicationContext(),
                                        notification
                                );
                            }
                        }
                    }
                    firstLoad = false;
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Foreground servis mora imati svoju (skrivenu) notifikaciju da bi Android
    // dozvolio servisu da ostane aktivan u pozadini — bez ovoga bi ga ubio.
    private Notification buildForegroundNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(FOREGROUND_CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    FOREGROUND_CHANNEL_ID,
                    "Pozadinska sinhronizacija",
                    NotificationManager.IMPORTANCE_MIN // IMPORTANCE_MIN = nema zvuka, nema ikone u statusbaru
            );
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                .setContentTitle("SaBoNa")
                .setContentText("Prijem obaveštenja...")
                .setSmallIcon(R.drawable.outline_chat_24) // koristimo postojeću ikonicu
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build();
    }
}