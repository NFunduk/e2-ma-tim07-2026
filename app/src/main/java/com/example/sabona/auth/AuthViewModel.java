package com.example.sabona.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class AuthViewModel extends ViewModel {

    private final AuthRepository repo = new AuthRepository();

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> registerSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loginSuccess    = new MutableLiveData<>();
    private final MutableLiveData<Boolean> verifiedSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> emailResent     = new MutableLiveData<>();

    public LiveData<Boolean> getLoading()        { return loading; }
    public LiveData<String>  getError()          { return error; }
    public LiveData<Boolean> getRegisterSuccess(){ return registerSuccess; }
    public LiveData<Boolean> getLoginSuccess()   { return loginSuccess; }
    public LiveData<Boolean> getVerifiedSuccess(){ return verifiedSuccess; }
    public LiveData<Boolean> getEmailResent()    { return emailResent; }

    public void register(String email, String username,
                         String password, String region) {
        loading.setValue(true);
        error.setValue(null);

        repo.register(email, username, password, region, new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                loading.postValue(false);
                registerSuccess.postValue(true);
            }
            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void login(String identifier, String password) {
        loading.setValue(true);
        error.setValue(null);

        repo.login(identifier, password, new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                loading.postValue(false);
                loginSuccess.postValue(true);
            }
            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void checkVerification() {
        loading.setValue(true);

        repo.checkVerification(new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                loading.postValue(false);
                verifiedSuccess.postValue(true);
            }
            @Override
            public void onError(String message) {
                loading.postValue(false);
                error.postValue(message);
            }
        });
    }

    public void resendEmail() {
        repo.resendVerification(new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                emailResent.postValue(true);
            }
            @Override
            public void onError(String message) {
                error.postValue(message);
            }
        });
    }

    public void logout() {
        repo.logout();
    }
}