package com.example.sabona.leaderboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class LeaderboardViewModel extends ViewModel {

    private final LeaderboardRepository repository = new LeaderboardRepository();

    private final MutableLiveData<List<LeaderboardEntry>> weeklyEntries = new MutableLiveData<>();
    private final MutableLiveData<List<LeaderboardEntry>> monthlyEntries = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public LiveData<List<LeaderboardEntry>> getWeeklyEntries() {
        return weeklyEntries;
    }

    public LiveData<List<LeaderboardEntry>> getMonthlyEntries() {
        return monthlyEntries;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public void loadWeekly() {
        loading.setValue(true);

        repository.loadWeeklyLeaderboard(new LeaderboardRepository.LeaderboardCallback() {
            @Override
            public void onSuccess(List<LeaderboardEntry> entries) {
                loading.postValue(false);
                weeklyEntries.postValue(entries);
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void loadMonthly() {
        loading.setValue(true);

        repository.loadMonthlyLeaderboard(new LeaderboardRepository.LeaderboardCallback() {
            @Override
            public void onSuccess(List<LeaderboardEntry> entries) {
                loading.postValue(false);
                monthlyEntries.postValue(entries);
            }

            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void loadAll() {
        loadWeekly();
        loadMonthly();
    }
}