package com.example.sabona.league;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel za ekran prikaza napretka kroz ligu.
 *
 * Eksponira:
 *  - trenutnu ligu i broj zvezda
 *  - event za promjenu lige (za dijalog)
 *  - stanje greške
 */
public class LeagueViewModel extends ViewModel {

    //  Podaci koji se prikazuju na ekranu                                  //

    private final MutableLiveData<Integer> starsLive  = new MutableLiveData<>(0);
    private final MutableLiveData<League>  leagueLive = new MutableLiveData<>(League.NULTA);
    private final MutableLiveData<String>  errorLive  = new MutableLiveData<>();

    /** One-shot event za promjenu lige (prikazuje dijalog) */
    private final MutableLiveData<LeagueChangeEvent> leagueChangeEvent = new MutableLiveData<>();

    //  Repozitorijum

    private final LeagueRepository repository = new LeagueRepository();

    //  Javni getteri (observe iz Fragmenta)

    public LiveData<Integer> getStars()             { return starsLive; }
    public LiveData<League>  getLeague()            { return leagueLive; }
    public LiveData<String>  getError()             { return errorLive; }
    public LiveData<LeagueChangeEvent> getLeagueChangeEvent() { return leagueChangeEvent; }

    //  Akcije

    /** Učitaj trenutne podatke iz Firestore-a. */
    public void loadData() {
        repository.loadLeagueData(new LeagueRepository.LeagueDataCallback() {
            @Override
            public void onSuccess(int stars, League league) {
                starsLive.setValue(stars);
                leagueLive.setValue(league);
            }

            @Override
            public void onError(String message) {
                errorLive.setValue(message);
            }
        });
    }

    /**
     * Primijeni promjenu zvezda (dobitak/gubitak) i ažuriraj UI.
     * Ako se liga promijenila, emituje event za dijalog.
     *
     * @param delta pozitivan (dobitak) ili negativan (gubitak)
     */
    public void applyStarChange(int delta) {
        repository.applyStarChange(delta, new LeagueRepository.LeagueCallback() {
            @Override
            public void onSuccess(LeagueManager.LeagueChangeResult result) {
                starsLive.setValue(result.newStars);
                leagueLive.setValue(result.newLeague);

                if (result.leagueChanged()) {
                    leagueChangeEvent.setValue(
                            new LeagueChangeEvent(result.oldLeague, result.newLeague,
                                    result.promoted == Boolean.TRUE));
                }
            }

            @Override
            public void onError(String message) {
                errorLive.setValue(message);
            }
        });
    }

    /** Primijeni mesečnu kaznu (poziva se na kraju ciklusa). */
    public void applyMonthlyPenalty() {
        repository.applyMonthlyPenalty(new LeagueRepository.LeagueCallback() {
            @Override
            public void onSuccess(LeagueManager.LeagueChangeResult result) {
                starsLive.setValue(result.newStars);
                leagueLive.setValue(result.newLeague);

                if (result.leagueChanged()) {
                    leagueChangeEvent.setValue(
                            new LeagueChangeEvent(result.oldLeague, result.newLeague,
                                    result.promoted == Boolean.TRUE));
                }
            }

            @Override
            public void onError(String message) {
                errorLive.setValue(message);
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Pomocni event model                                                 //
    // ------------------------------------------------------------------ //

    public static class LeagueChangeEvent {
        public final League  oldLeague;
        public final League  newLeague;
        public final boolean promoted; // true = napredovao, false = pao

        public LeagueChangeEvent(League oldLeague, League newLeague, boolean promoted) {
            this.oldLeague = oldLeague;
            this.newLeague = newLeague;
            this.promoted  = promoted;
        }
    }
}