package com.example.sabona.repository;

import androidx.annotation.Nullable;

import com.example.sabona.model.AppNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class NotificationRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void createNotification(String userId, AppNotification notification) {
        db.collection("users")
                .document(userId)
                .collection("notifications")
                .add(notification);
    }

    public void markAsRead(String notificationId) {
        String uid = getCurrentUid();
        if (uid == null || notificationId == null) return;

        db.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notificationId)
                .update("read", true);
    }

    public void markAsHandled(String notificationId) {
        String uid = getCurrentUid();
        if (uid == null || notificationId == null) return;

        db.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notificationId)
                .update(
                        "read", true,
                        "handled", true
                );
    }

    public void markAllAsRead() {
        String uid = getCurrentUid();
        if (uid == null) return;

        db.collection("users")
                .document(uid)
                .collection("notifications")
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        doc.getReference().update("read", true);
                    }
                });
    }

    @Nullable
    private String getCurrentUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return null;
        }

        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
}