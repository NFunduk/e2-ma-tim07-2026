package com.example.sabona.friends;

import com.example.sabona.model.AppNotification;
import com.example.sabona.model.FriendRequest;
import com.example.sabona.model.FriendUser;
import com.example.sabona.repository.NotificationFactory;
import com.example.sabona.repository.NotificationRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.os.Handler;
import android.os.Looper;

/**
 * Repository za funkcionalnost Prijatelji (tačka 7).
 *
 * Firestore struktura:
 *  users/{uid}/friends/{friendUid}   → { uid, username, stars, league, avatarRes }
 *  friendRequests/{docId}            → FriendRequest
 *  gameRequests/{docId}              → { fromUid, toUid, status, createdAt }
 */
public class FriendsRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    private final FirebaseFirestore db   = FirebaseFirestore.getInstance();
    private final FirebaseAuth      auth = FirebaseAuth.getInstance();
    private final NotificationRepository notifRepo = new NotificationRepository();

    // ─── helpers ────────────────────────────────────────────────────────────

    private String myUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    // ─── helper: parsiranje FriendUser iz Firestore dokumenta ───────────────

    private FriendUser parseFriendUser(DocumentSnapshot doc) {
        FriendUser fu = new FriendUser(
                doc.getId(),
                doc.getString("username"),
                doc.getString("avatarRes"),
                doc.getLong("stars") != null ? doc.getLong("stars").intValue() : 0,
                doc.getLong("league") != null ? doc.getLong("league").intValue() : 0
        );
        fu.setMonthlyRank(doc.getLong("monthlyRank") != null ? doc.getLong("monthlyRank").intValue() : 0);
        fu.setOnline(Boolean.TRUE.equals(doc.getBoolean("online")));
        fu.setInGame(Boolean.TRUE.equals(doc.getBoolean("inGame")));
        return fu;
    }

    // ─── pretraga korisnika po username ─────────────────────────────────────

    public void searchByUsername(String query, Callback<List<FriendUser>> cb) {
        String uid = myUid();
        if (uid == null) { cb.onError("Nije prijavljen"); return; }

        String queryLower = query.trim().toLowerCase();
        if (queryLower.isEmpty()) { cb.onSuccess(new ArrayList<>()); return; }

        db.collection("users")
                .orderBy("username")
                .startAt(query.trim())
                .endAt(query.trim() + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener(snap -> {
                    List<FriendUser> results = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        if (uid.equals(doc.getId())) continue; // preskoči sebe
                        results.add(parseFriendUser(doc));
                    }
                    cb.onSuccess(results);
                })
                .addOnFailureListener(e -> cb.onError("Greška pri pretrazi: " + e.getMessage()));
    }

    // ─── učitaj listu prijatelja ─────────────────────────────────────────────

    /**
     * Učitava listu prijatelja. Lista UID-ova prijatelja se čuva u
     * users/{uid}/friends (upisana jednom, pri prihvatanju zahteva), ali da bi
     * status "online"/"u partiji"/zvezde/liga/rang bili AŽURNI u trenutku
     * prikaza, svaki prijatelj se naknadno dohvata direktno iz users/{friendUid}.
     */
    public void loadFriends(Callback<List<FriendUser>> cb) {
        String uid = myUid();
        if (uid == null) { cb.onError("Nije prijavljen"); return; }

        db.collection("users").document(uid).collection("friends")
                .get()
                .addOnSuccessListener(snap -> {
                    List<String> friendUids = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        friendUids.add(doc.getId());
                    }

                    if (friendUids.isEmpty()) {
                        cb.onSuccess(new ArrayList<>());
                        return;
                    }

                    fetchFreshFriendData(friendUids, cb);
                })
                .addOnFailureListener(e -> cb.onError("Greška pri učitavanju prijatelja"));
    }

    /** Dohvata trenutne podatke (online/inGame/stars/league/rank) za svaki uid iz users kolekcije. */
    private void fetchFreshFriendData(List<String> friendUids, Callback<List<FriendUser>> cb) {
        List<FriendUser> list = new ArrayList<>();
        int[] remaining = { friendUids.size() };

        for (String friendUid : friendUids) {
            db.collection("users").document(friendUid).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                            list.add(parseFriendUser(task.getResult()));
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            cb.onSuccess(list);
                        }
                    });
        }
    }

    // ─── pošalji zahtev za prijatelja ────────────────────────────────────────

    public void sendFriendRequest(FriendUser targetUser, Callback<Void> cb) {
        String uid = myUid();
        if (uid == null) {
            cb.onError("Nije prijavljen");
            return;
        }

        FirebaseUser me = auth.getCurrentUser();

        // Provjeri da li su već prijatelji
        db.collection("users").document(uid)
                .collection("friends").document(targetUser.getUid())
                .get()
                .addOnSuccessListener(existDoc -> {
                    if (existDoc.exists()) {
                        cb.onError("Već ste prijatelji");
                        return;
                    }

                    // Provjeri da li već postoji pending zahtev
                    db.collection("friendRequests")
                            .whereEqualTo("fromUid", uid)
                            .whereEqualTo("toUid", targetUser.getUid())
                            .whereEqualTo("status", "pending")
                            .get()
                            .addOnSuccessListener(existReqs -> {
                                if (!existReqs.isEmpty()) {
                                    cb.onError("Zahtev je već poslan");
                                    return;
                                }

                                // Kreiraj zahtev
                                String senderName = me != null && me.getDisplayName() != null
                                        ? me.getDisplayName()
                                        : "Igrač";

                                // Pokušaj da dobiješ username iz Firestore
                                db.collection("users").document(uid).get()
                                        .addOnSuccessListener(myDoc -> {

                                            String myUsername = myDoc.getString("username");
                                            if (myUsername == null) {
                                                myUsername = senderName;
                                            }

                                            final String myUsernameFinal = myUsername;

                                            FriendRequest req = new FriendRequest(
                                                    uid,
                                                    myUsernameFinal,
                                                    targetUser.getUid()
                                            );

                                            db.collection("friendRequests")
                                                    .add(req)
                                                    .addOnSuccessListener(docRef -> {

                                                        // Pošalji notifikaciju primaocu
                                                        AppNotification notif =
                                                                NotificationFactory.friendRequest(
                                                                        docRef.getId(),
                                                                        myUsernameFinal
                                                                );

                                                        notifRepo.createNotification(
                                                                targetUser.getUid(),
                                                                notif
                                                        );

                                                        cb.onSuccess(null);
                                                    })
                                                    .addOnFailureListener(
                                                            e -> cb.onError("Greška pri slanju zahteva")
                                                    );

                                        })
                                        .addOnFailureListener(
                                                e -> cb.onError("Greška pri čitanju korisnika")
                                        );

                            })
                            .addOnFailureListener(
                                    e -> cb.onError("Greška: " + e.getMessage())
                            );

                })
                .addOnFailureListener(
                        e -> cb.onError("Greška: " + e.getMessage())
                );
    }
    // ─── prihvati zahtev za prijatelja ───────────────────────────────────────

    public void acceptFriendRequest(String requestId, Callback<Void> cb) {
        db.collection("friendRequests").document(requestId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { cb.onError("Zahtev nije pronađen"); return; }

                    String fromUid  = doc.getString("fromUid");
                    String toUid    = doc.getString("toUid");

                    if (fromUid == null || toUid == null) { cb.onError("Neispravan zahtev"); return; }

                    // Učitaj podatke oba korisnika
                    db.collection("users").document(fromUid).get()
                            .addOnSuccessListener(fromDoc -> {
                                db.collection("users").document(toUid).get()
                                        .addOnSuccessListener(toDoc -> {

                                            Map<String, Object> fromData = buildFriendData(fromDoc);
                                            Map<String, Object> toData   = buildFriendData(toDoc);

                                            // Dodaj prijatelje jedni drugima
                                            db.collection("users").document(toUid)
                                                    .collection("friends").document(fromUid).set(fromData);

                                            db.collection("users").document(fromUid)
                                                    .collection("friends").document(toUid).set(toData);

                                            // Ažuriraj status zahteva
                                            doc.getReference().update("status", "accepted");

                                            cb.onSuccess(null);
                                        })
                                        .addOnFailureListener(e -> cb.onError("Greška pri čitanju korisnika"));
                            })
                            .addOnFailureListener(e -> cb.onError("Greška pri čitanju korisnika"));
                })
                .addOnFailureListener(e -> cb.onError("Zahtev nije pronađen"));
    }

    // ─── odbij zahtev za prijatelja ──────────────────────────────────────────

    public void rejectFriendRequest(String requestId, Callback<Void> cb) {
        db.collection("friendRequests").document(requestId)
                .update("status", "rejected")
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError("Greška pri odbijanju zahteva"));
    }

    // ─── pošalji poziv za partiju ────────────────────────────────────────────

    /**
     * Šalje poziv za partiju prijatelju. Prethodno provjerava da je prijatelj
     * ulogovan (online) i da trenutno ne učestvuje u nekoj partiji (inGame),
     * čitajući svežu kopiju iz users/{friendUid} (ne pouzdaje se u eventualno
     * zastareo objekat dobijen iz liste prijatelja u UI-u).
     */
    public void sendGameInvite(FriendUser friend, Callback<String> cb) {
        String uid = myUid();
        if (uid == null) { cb.onError("Nije prijavljen"); return; }

        db.collection("users").document(friend.getUid()).get()
                .addOnSuccessListener(friendDoc -> {

                    boolean online = Boolean.TRUE.equals(friendDoc.getBoolean("online"));
                    boolean inGame = Boolean.TRUE.equals(friendDoc.getBoolean("inGame"));

                    if (!online) {
                        cb.onError(friend.getUsername() + " trenutno nije ulogovan/a.");
                        return;
                    }
                    if (inGame) {
                        cb.onError(friend.getUsername() + " je trenutno u partiji.");
                        return;
                    }

                    doSendGameInvite(uid, friend, cb);
                })
                .addOnFailureListener(e -> cb.onError("Greška pri proveri statusa prijatelja"));
    }

    private void doSendGameInvite(String uid, FriendUser friend, Callback<String> cb) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(myDoc -> {
                    String myUsername = myDoc.getString("username");
                    if (myUsername == null) myUsername = "Igrač";

                    Map<String, Object> reqData = new HashMap<>();
                    reqData.put("fromUid",  uid);
                    reqData.put("toUid",    friend.getUid());
                    reqData.put("status",   "pending");
                    reqData.put("createdAt", Timestamp.now());

                    final String myUsernameFinal = myUsername;

                    db.collection("gameRequests").add(reqData)
                            .addOnSuccessListener(docRef -> {

                                // Pošalji notifikaciju prijatelju
                                AppNotification notif = NotificationFactory.gameInvite(
                                        docRef.getId(),
                                        myUsernameFinal);

                                notifRepo.createNotification(
                                        friend.getUid(),
                                        notif
                                );

                                // Automatsko odbijanje nakon 10 sekundi
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {

                                    db.collection("gameRequests")
                                            .document(docRef.getId())
                                            .get()
                                            .addOnSuccessListener(snapshot -> {

                                                if (!snapshot.exists()) return;

                                                String status = snapshot.getString("status");

                                                if ("pending".equals(status)) {

                                                    snapshot.getReference()
                                                            .update("status", "rejected");

                                                }

                                            });

                                }, 10000);

                                cb.onSuccess(docRef.getId());
                            })
                            .addOnFailureListener(e -> cb.onError("Greška pri slanju poziva"));
                })
                .addOnFailureListener(e -> cb.onError("Greška pri čitanju korisnika"));
    }

    // ─── otkaži game invite ──────────────────────────────────────────────────

    public void cancelGameInvite(String gameRequestId, Callback<Void> cb) {
        db.collection("gameRequests").document(gameRequestId)
                .update("status", "cancelled")
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError("Greška pri otkazivanju poziva"));
    }

    // ─── slušaj na status game request-a ─────────────────────────────────────

    public static class GameRequestUpdate {
        public final String status;
        public final String sessionId;
        public GameRequestUpdate(String status, String sessionId) {
            this.status = status;
            this.sessionId = sessionId;
        }
    }

    public com.google.firebase.firestore.ListenerRegistration listenGameRequest(
            String gameRequestId,
            Callback<GameRequestUpdate> statusCallback) {

        return db.collection("gameRequests").document(gameRequestId)
                .addSnapshotListener((snap, err) -> {
                    if (err != null || snap == null || !snap.exists()) return;
                    String status = snap.getString("status");
                    String sessionId = snap.getString("sessionId");
                    if (status != null) statusCallback.onSuccess(new GameRequestUpdate(status, sessionId));
                });
    }

    // ─── lookup korisnika po UID (za QR skeniranje) ──────────────────────────

    public void getUserByUid(String uid, Callback<FriendUser> cb) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { cb.onError("Korisnik nije pronađen"); return; }
                    cb.onSuccess(parseFriendUser(doc));
                })
                .addOnFailureListener(e -> cb.onError("Korisnik nije pronađen"));
    }

    // ─── helper ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildFriendData(DocumentSnapshot doc) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid",       doc.getId());
        data.put("username",  doc.getString("username"));
        data.put("avatarRes", doc.getString("avatarRes"));
        data.put("stars",     doc.getLong("stars") != null ? doc.getLong("stars") : 0L);
        data.put("league",    doc.getLong("league") != null ? doc.getLong("league") : 0L);
        return data;
    }
}