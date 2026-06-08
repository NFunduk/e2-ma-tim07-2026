package com.example.sabona.viewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Map;

public class ProfileViewModel extends ViewModel {

    private final UserRepository repo = new UserRepository();

    private final MutableLiveData<Map<String, Object>> userData  = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Object>> statsData = new MutableLiveData<>();
    private final MutableLiveData<String>  error         = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading       = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> avatarUpdated = new MutableLiveData<>();

    public LiveData<Map<String, Object>> getUserData()    { return userData; }
    public LiveData<Map<String, Object>> getStatsData()   { return statsData; }
    public LiveData<String>  getError()        { return error; }
    public LiveData<Boolean> getLoading()      { return loading; }
    public LiveData<Boolean> getAvatarUpdated(){ return avatarUpdated; }

    /** Pozovi kad korisnik se izloguje – briše kešovane podatke */
    public void clearData() {
        userData.setValue(null);
        statsData.setValue(null);
        error.setValue(null);
        avatarUpdated.setValue(null);
    }

    public void loadUser() {
        // Ako nema ulogovanog korisnika, ne učitavaj ništa
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        loading.setValue(true);
        repo.getCurrentUser(new UserRepository.UserCallback<Map<String, Object>>() {
            @Override public void onSuccess(Map<String, Object> data) {
                userData.postValue(data);
                loading.postValue(false);
            }
            @Override public void onError(String msg) {
                error.postValue(msg);
                loading.postValue(false);
            }
        });
    }

    public void loadStats() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        repo.getStats(new UserRepository.UserCallback<Map<String, Object>>() {
            @Override public void onSuccess(Map<String, Object> data) {
                statsData.postValue(data);
            }
            @Override public void onError(String msg) { /* tiha greška */ }
        });
    }

    public void updateAvatar(String avatarResName) {
        // Gost (neregistrovan) ne može mijenjati avatar
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            error.setValue("Prijavite se da biste promijenili avatar.");
            return;
        }

        repo.updateAvatar(avatarResName, new UserRepository.UserCallback<Void>() {
            @Override public void onSuccess(Void data) {
                Map<String, Object> current = userData.getValue();
                if (current != null) {
                    current.put("avatarRes", avatarResName);
                    userData.postValue(current);
                }
                avatarUpdated.postValue(true);
            }
            @Override public void onError(String msg) {
                error.postValue(msg);
            }
        });
    }

    public void resetAvatarUpdated() { avatarUpdated.setValue(null); }
}