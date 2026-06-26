package com.example.sabona.challenge;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class ChallengeViewModel extends ViewModel {

    private final ChallengeRepository repo = new ChallengeRepository();

    private final MutableLiveData<List<Challenge>> challenges = new MutableLiveData<>();
    private final MutableLiveData<String>          error      = new MutableLiveData<>();
    private final MutableLiveData<Boolean>         loading    = new MutableLiveData<>(false);

    private ListenerRegistration listenerReg;
    private String currentRegion;

    public LiveData<List<Challenge>> getChallenges() { return challenges; }
    public LiveData<String>          getError()      { return error; }
    public LiveData<Boolean>         getLoading()    { return loading; }

    public void startListening(String region) {
        if (region == null) return;
        if (region.equals(currentRegion) && listenerReg != null) return;
        currentRegion = region;
        stopListening();
        startListeningInternal(region);
    }

    private void startListeningInternal(String region) {
        listenerReg = repo.listenChallenges(region, new ChallengeRepository.ListCallback() {
            @Override public void onSuccess(List<Challenge> list) { challenges.setValue(list); }
            @Override public void onError(String msg)            { error.setValue(msg); }
            @Override public void onRetry() {
                // Index još nije gotov — ugasi stari listener i pokušaj ponovo
                stopListening();
                startListeningInternal(region);
            }
        });
    }

    public void stopListening() {
        if (listenerReg != null) { listenerReg.remove(); listenerReg = null; }
    }

    public void createChallenge(Challenge challenge) {
        loading.setValue(true);
        repo.createChallenge(challenge, new ChallengeRepository.Callback() {
            @Override public void onSuccess() { loading.setValue(false); }
            @Override public void onError(String msg) {
                loading.setValue(false);
                error.setValue(msg);
            }
        });
    }

    public void joinChallenge(String challengeId, String uid, String username,
                              int starsWager, int tokensWager) {
        loading.setValue(true);
        repo.joinChallenge(challengeId, uid, username, starsWager, tokensWager,
                new ChallengeRepository.Callback() {
                    @Override public void onSuccess() { loading.setValue(false); }
                    @Override public void onError(String msg) {
                        loading.setValue(false);
                        error.setValue(msg);
                    }
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }
}
