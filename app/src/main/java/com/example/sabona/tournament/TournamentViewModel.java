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
    private ListenerRegistration tournamentPlayersListener;
    private boolean started = false;
    private boolean matched = false;
    private String subscribedTournamentId = null;

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
                listener = repo.listenMyQueueStatus(new TournamentRepository.Callback<TournamentRepository.QueueUpdate>() {
                    @Override
                    public void onSuccess(TournamentRepository.QueueUpdate result) {
                        // Čim je poznat tournamentId, prestani da vučeš prikaz iz
                        // "waiting" reda (koji se menja nezavisno na svakom uređaju)
                        // i preveži se na sam tournament dokument - isti je za sva
                        // 4 uređaja, pa je prikaz garantovano konzistentan.
                        if (result.tournamentId != null
                                && !result.tournamentId.equals(subscribedTournamentId)) {
                            subscribedTournamentId = result.tournamentId;

                            if (playersListener != null) {
                                playersListener.remove();
                                playersListener = null;
                            }
                            if (tournamentPlayersListener != null) {
                                tournamentPlayersListener.remove();
                            }

                            tournamentPlayersListener = repo.listenTournamentPlayers(
                                    result.tournamentId,
                                    new TournamentRepository.Callback<List<TournamentRepository.TournamentPlayer>>() {
                                        @Override
                                        public void onSuccess(List<TournamentRepository.TournamentPlayer> list) {
                                            players.postValue(list);
                                        }

                                        @Override
                                        public void onError(String message) { }
                                    });
                        }

                        if (matched) return;

                        if ("matched".equals(result.status) && result.sessionId != null) {
                            matched = true;
                            state.postValue(State.MATCHED);
                            sessionId.postValue(result.sessionId);

                            handler.removeCallbacks(retryRunnable);
                        }
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
        if (tournamentPlayersListener != null) tournamentPlayersListener.remove();
        if (listener != null) listener.remove();

        handler.removeCallbacks(retryRunnable);
        repo.cancelQueue();
    }

    @Override
    protected void onCleared() {
        if (playersListener != null) playersListener.remove();
        if (tournamentPlayersListener != null) tournamentPlayersListener.remove();
        super.onCleared();

        if (listener != null) listener.remove();
        handler.removeCallbacks(retryRunnable);
    }
}