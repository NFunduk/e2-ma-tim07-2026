package com.example.sabona.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.model.ChatMessage;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class ChatViewModel extends ViewModel {

    private final ChatRepository repo = new ChatRepository();

    private final MutableLiveData<List<ChatMessage>> messagesLive = new MutableLiveData<>();
    public LiveData<List<ChatMessage>> getMessages() { return messagesLive; }

    private final MutableLiveData<String> regionLive = new MutableLiveData<>();
    public LiveData<String> getRegion() { return regionLive; }

    private final MutableLiveData<String> errorLive = new MutableLiveData<>();
    public LiveData<String> getError() { return errorLive; }

    private ListenerRegistration messagesListener;
    private boolean started = false;

    /** Učita region ulogovanog igrača i pokrene real-time slušanje poruka njegove sobe. */
    public void start() {
        if (started) return;
        started = true;

        repo.loadCurrentUserRegion(new ChatRepository.Callback<String>() {
            @Override
            public void onSuccess(String region) {
                regionLive.setValue(region);

                messagesListener = repo.listenForMessages(region, new ChatRepository.Callback<List<ChatMessage>>() {
                    @Override
                    public void onSuccess(List<ChatMessage> result) {
                        messagesLive.setValue(result);
                    }

                    @Override
                    public void onError(String message) {
                        errorLive.setValue(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                errorLive.setValue(message);
            }
        });
    }

    public void sendMessage(String text) {
        String region = regionLive.getValue();
        if (region == null) return;

        repo.sendMessage(region, text, new ChatRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) { }

            @Override
            public void onError(String message) {
                errorLive.setValue(message);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }
}