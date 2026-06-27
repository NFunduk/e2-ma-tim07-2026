package com.example.sabona.matchmaking;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;

public class MatchmakingViewModel extends ViewModel {

    public enum State { SEARCHING, WAITING, MATCHED, TIMEOUT, ERROR }

    private final MutableLiveData<State>   state       = new MutableLiveData<>(State.SEARCHING);
    private final MutableLiveData<String>  errorMsg    = new MutableLiveData<>();
    // [0]=sessionId, [1]="1" ako sam host inače "0"
    private final MutableLiveData<String[]> matchResult = new MutableLiveData<>();

    private final MatchmakingRepository repo = new MatchmakingRepository();
    private ListenerRegistration listener;
    private boolean started = false;
    private boolean matched = false;
    private final Runnable timeoutRunnable = () -> {
        if (!matched && started) {
            state.postValue(State.TIMEOUT);
        }
    };

    public LiveData<State>    getState()       { return state; }
    public LiveData<String>   getErrorMsg()    { return errorMsg; }
    public LiveData<String[]> getMatchResult() { return matchResult; }

    public void startSearching(String myUid) {
        if (started) return;
        started = true;

        repo.joinQueue(new MatchmakingRepository.Callback<Void>() {
            @Override public void onSuccess(Void result) {
                listener = repo.listenForMatch(new MatchmakingRepository.Callback<String>() {
                    @Override public void onSuccess(String sessionId) { onMatchedAsGuest(sessionId); }
                    @Override public void onError(String message) { /* tiho — listener i dalje radi */ }
                });
                attemptFind();
                retryHandler.postDelayed(retryRunnable, 3000);
                retryHandler.postDelayed(timeoutRunnable, 60000);
            }
            @Override public void onError(String message) {
                state.postValue(State.ERROR);
                errorMsg.postValue(message);
                started = false;
            }
        });
    }

    private final android.os.Handler retryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!matched && started) {
                attemptFind();
                retryHandler.postDelayed(this, 3000); // pokušaj svake 3 sekunde
            }
        }
    };

    private void attemptFind() {
        repo.findOpponentAndMatch(new MatchmakingRepository.Callback<String[]>() {
            @Override public void onSuccess(String[] result) {
                if (result != null) onMatchedAsHost(result[0]);
                else if (!matched) state.postValue(State.WAITING);
            }
            @Override public void onError(String message) {
                // tiha greška
            }
        });
    }

    private void onMatchedAsHost(String sessionId) {
        if (matched) return;
        matched = true;
        if (listener != null) listener.remove();
        matchResult.postValue(new String[]{sessionId, "1"});
        state.postValue(State.MATCHED);
    }

    private void onMatchedAsGuest(String sessionId) {
        if (matched) return;
        matched = true;
        if (listener != null) listener.remove();
        matchResult.postValue(new String[]{sessionId, "0"});
        state.postValue(State.MATCHED);
    }

    public void cancel() {
        if (matched) return; // već smo upareni, kasno za otkazivanje
        if (listener != null) listener.remove();
        retryHandler.removeCallbacks(retryRunnable);
        retryHandler.removeCallbacks(timeoutRunnable);
        repo.cancelQueue();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listener != null) listener.remove();
        retryHandler.removeCallbacks(retryRunnable);
        retryHandler.removeCallbacks(timeoutRunnable);
    }
}
