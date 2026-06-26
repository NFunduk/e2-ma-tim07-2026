package com.example.sabona.tournament;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.List;

import com.google.firebase.firestore.ListenerRegistration;

public class TournamentViewModel extends ViewModel {

    public enum State {
        SEARCHING,
        MATCHED,
        ERROR
    }

    private final TournamentRepository repo = new TournamentRepository();

    private final MutableLiveData<State> state = new MutableLiveData<>(State.SEARCHING);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> sessionId = new MutableLiveData<>();

    private final MutableLiveData<List<TournamentRepository.TournamentPlayer>> players =
            new MutableLiveData<>();

    private ListenerRegistration listener;
    private boolean started = false;
    private boolean matched = false;

    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable retryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!matched && started) {
                repo.tryCreateTournament(new TournamentRepository.Callback<String>() {
                    @Override public void onSuccess(String result) { }
                    @Override public void onError(String message) { }
                });

                handler.postDelayed(this, 3000);
            }
        }
    };

    public LiveData<State> getState() {
        return state;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<String> getSessionId() {
        return sessionId;
    }

    public LiveData<List<TournamentRepository.TournamentPlayer>> getPlayers() {
        return players;
    }

    private ListenerRegistration playersListener;

    public void start() {
        if (started) return;
        started = true;

        repo.joinTournamentQueue(new TournamentRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                playersListener = repo.listenWaitingPlayers(new TournamentRepository.Callback<List<TournamentRepository.TournamentPlayer>>() {
                    @Override
                    public void onSuccess(List<TournamentRepository.TournamentPlayer> result) {
                        players.postValue(result);
                    }

                    @Override
                    public void onError(String message) { }
                });
                listener = repo.listenMyTournamentMatch(new TournamentRepository.Callback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        if (matched) return;

                        matched = true;
                        state.postValue(State.MATCHED);
                        sessionId.postValue(result);

                        if (listener != null) listener.remove();
                        handler.removeCallbacks(retryRunnable);
                    }

                    @Override
                    public void onError(String message) { }
                });

                repo.tryCreateTournament(new TournamentRepository.Callback<String>() {
                    @Override public void onSuccess(String result) { }
                    @Override public void onError(String message) { }
                });

                handler.postDelayed(retryRunnable, 3000);
            }

            @Override
            public void onError(String message) {
                state.postValue(State.ERROR);
                error.postValue(message);
                started = false;
            }
        });
    }

    public void cancel() {
        if (matched) return;

        if (playersListener != null) playersListener.remove();
        if (listener != null) listener.remove();

        handler.removeCallbacks(retryRunnable);
        repo.cancelQueue();
    }

    @Override
    protected void onCleared() {
        if (playersListener != null) playersListener.remove();
        super.onCleared();

        if (listener != null) listener.remove();
        handler.removeCallbacks(retryRunnable);
    }
}