package com.example.sabona.friends;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.model.FriendUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class FriendsViewModel extends ViewModel {

    private final FriendsRepository repo = new FriendsRepository();

    // Lista prijatelja
    private final MutableLiveData<List<FriendUser>> friendsLive = new MutableLiveData<>();
    public LiveData<List<FriendUser>> getFriends() { return friendsLive; }

    // Rezultati pretrage
    private final MutableLiveData<List<FriendUser>> searchResultsLive = new MutableLiveData<>();
    public LiveData<List<FriendUser>> getSearchResults() { return searchResultsLive; }

    // Poruke (greška / uspjeh)
    private final MutableLiveData<String> messageLive = new MutableLiveData<>();
    public LiveData<String> getMessage() { return messageLive; }

    // Status aktivnog game invite-a (requestId koji smo poslali)
    private final MutableLiveData<String> pendingGameRequestId = new MutableLiveData<>();
    public LiveData<String> getPendingGameRequestId() { return pendingGameRequestId; }

    // Status odgovora na game invite ("accepted" / "rejected" / "cancelled")
    private final MutableLiveData<String> gameInviteStatus = new MutableLiveData<>();
    public LiveData<String> getGameInviteStatus() { return gameInviteStatus; }

    private ListenerRegistration gameRequestListener;

    private final MutableLiveData<String> matchSessionReady = new MutableLiveData<>();
    public LiveData<String> getMatchSessionReady() { return matchSessionReady; }
    public void clearMatchSessionReady() { matchSessionReady.setValue(null); }

    // ─── učitaj prijatelje ───────────────────────────────────────────────────

    public void loadFriends() {
        repo.loadFriends(new FriendsRepository.Callback<List<FriendUser>>() {
            @Override public void onSuccess(List<FriendUser> result) { friendsLive.setValue(result); }
            @Override public void onError(String message)            { messageLive.setValue(message); }
        });
    }

    // ─── pretraži ────────────────────────────────────────────────────────────

    public void search(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchResultsLive.setValue(null);
            return;
        }
        repo.searchByUsername(query, new FriendsRepository.Callback<List<FriendUser>>() {
            @Override public void onSuccess(List<FriendUser> result) { searchResultsLive.setValue(result); }
            @Override public void onError(String message)            { messageLive.setValue(message); }
        });
    }

    public void clearSearch() {
        searchResultsLive.setValue(null);
    }

    // ─── pošalji zahtev za prijatelja ────────────────────────────────────────

    public void sendFriendRequest(FriendUser user) {
        repo.sendFriendRequest(user, new FriendsRepository.Callback<Void>() {
            @Override public void onSuccess(Void result) { messageLive.setValue("Zahtev za prijateljstvo je poslan!"); }
            @Override public void onError(String message) { messageLive.setValue(message); }
        });
    }

    // ─── pošalji game invite ──────────────────────────────────────────────────

    public void sendGameInvite(FriendUser friend) {
        repo.sendGameInvite(friend, new FriendsRepository.Callback<String>() {
            @Override
            public void onSuccess(String requestId) {
                pendingGameRequestId.setValue(requestId);

                gameRequestListener = repo.listenGameRequest(requestId,
                        new FriendsRepository.Callback<FriendsRepository.GameRequestUpdate>() {
                            @Override
                            public void onSuccess(FriendsRepository.GameRequestUpdate update) {
                                if ("accepted".equals(update.status)) {
                                    if (update.sessionId != null) {
                                        // Prijatelj je prihvatio I kreirao partiju — krećemo kao host
                                        gameInviteStatus.setValue("accepted");
                                        matchSessionReady.setValue(update.sessionId);
                                        stopListeningGameRequest();
                                    }
                                    // ako sessionId još nije stigao, čekamo sledeći snapshot
                                } else if (!"pending".equals(update.status)) {
                                    gameInviteStatus.setValue(update.status);
                                    stopListeningGameRequest();
                                }
                            }
                            @Override public void onError(String message) {}
                        });
            }
            @Override public void onError(String message) { messageLive.setValue(message); }
        });
    }

    public void cancelGameInvite(String requestId) {
        stopListeningGameRequest();
        repo.cancelGameInvite(requestId, new FriendsRepository.Callback<Void>() {
            @Override public void onSuccess(Void result) { messageLive.setValue("Poziv je otkazan."); }
            @Override public void onError(String message) { messageLive.setValue(message); }
        });
        pendingGameRequestId.setValue(null);
        gameInviteStatus.setValue(null);
    }

    public void clearGameInviteStatus() {
        gameInviteStatus.setValue(null);
        pendingGameRequestId.setValue(null);
    }

    // ─── lookup po UID (QR skeniranje) ───────────────────────────────────────

    public void lookupByUid(String uid) {
        repo.getUserByUid(uid, new FriendsRepository.Callback<FriendUser>() {
            @Override
            public void onSuccess(FriendUser result) {
                // Ponudi slanje zahteva – prikazujemo kao search result
                java.util.List<FriendUser> list = new java.util.ArrayList<>();
                list.add(result);
                searchResultsLive.setValue(list);
            }
            @Override public void onError(String message) { messageLive.setValue("QR: " + message); }
        });
    }

    private void stopListeningGameRequest() {
        if (gameRequestListener != null) {
            gameRequestListener.remove();
            gameRequestListener = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListeningGameRequest();
    }
}