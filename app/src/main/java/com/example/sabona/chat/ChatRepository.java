package com.example.sabona.chat;

import com.example.sabona.daily.DailyMissionRepository;
import com.example.sabona.model.ChatMessage;
import com.example.sabona.repository.NotificationFactory;
import com.example.sabona.repository.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository za funkcionalnost Čet (tačka 8 specifikacije).
 *
 * Firestore struktura:
 *  regionChats/{region}/messages/{messageId} → ChatMessage
 *
 * Svi igrači koji pripadaju istom regionu dele istu "sobu" za čet
 * (8.a). Poruke se prate u realnom vremenu preko Firestore
 * snapshot listenera (8.b). Pri slanju poruke, svi članovi regiona
 * koji trenutno nisu u aplikaciji (User.online == false) dobijaju
 * sistemsku notifikaciju (8.e).
 */
public class ChatRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final NotificationRepository notifRepo = new NotificationRepository();
    private final DailyMissionRepository dailyMissionRepository = new DailyMissionRepository();

    /** Učitava region ulogovanog igrača — potreban pre ulaska u sobu za čet. */
    public void loadCurrentUserRegion(Callback<String> callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("Korisnik nije ulogovan");
            return;
        }

        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String region = doc.getString("region");
                    if (region == null || region.trim().isEmpty()) {
                        callback.onError("Region nije podešen za ovog igrača");
                    } else {
                        callback.onSuccess(region);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Sluša poruke u realnom vremenu za dati region, hronološki sortirane.
     * Vraća ListenerRegistration koju pozivalac mora ukloniti (npr. u
     * onCleared ViewModel-a) kad više nije potrebna.
     */
    public ListenerRegistration listenForMessages(String region, Callback<List<ChatMessage>> callback) {
        return db.collection("regionChats")
                .document(region)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) {
                        callback.onError(error != null ? error.getMessage() : "Greška pri učitavanju poruka");
                        return;
                    }

                    List<ChatMessage> messages = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ChatMessage message = doc.toObject(ChatMessage.class);
                        if (message != null) {
                            message.setId(doc.getId());
                            messages.add(message);
                        }
                    }
                    callback.onSuccess(messages);
                });
    }

    /** Šalje poruku u sobu za čet datog regiona. */
    public void sendMessage(String region, String text, Callback<Void> callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("Korisnik nije ulogovan");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    String senderName = userDoc.getString("username");
                    if (senderName == null) senderName = "Igrač";
                    String finalSenderName = senderName;

                    ChatMessage message = new ChatMessage(uid, finalSenderName, text.trim());

                    db.collection("regionChats")
                            .document(region)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(ref -> {
                                callback.onSuccess(null);
                                dailyMissionRepository.completeSendChat(uid, null);
                                notifyOfflineRegionMembers(region, uid, finalSenderName);
                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Šalje sistemsku notifikaciju o novoj poruci svim igračima istog regiona
     * koji trenutno nisu u aplikaciji (8.e). "online" se ažurira preko
     * PresenceManager-a dok je aplikacija u prvom planu.
     */
    private void notifyOfflineRegionMembers(String region, String senderUid, String senderName) {
        db.collection("users")
                .whereEqualTo("region", region)
                .whereEqualTo("online", false)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (doc.getId().equals(senderUid)) continue;
                        notifRepo.createNotification(
                                doc.getId(),
                                NotificationFactory.chatMessage(senderName)
                        );
                    }
                });
        // Namerno bez .addOnFailureListener: ako obaveštavanje ne uspe,
        // poruka je već uspešno poslata i to ne treba prikazivati kao grešku.
    }
}