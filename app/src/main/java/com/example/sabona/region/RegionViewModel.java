package com.example.sabona.region;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Map;

/**
 * ViewModel za ekran "Prikaz regiona".
 *
 * Eksponira:
 *  - listu tačaka za mapu (jedna po registrovanom igraču)
 *  - statistiku po regionu (za dijalog prikazan na klik na region)
 *  - trenutni region ulogovanog igrača (da se njegova zona posebno označi)
 */
public class RegionViewModel extends ViewModel {

    private final MutableLiveData<List<RegionMapPoint>> mapPoints = new MutableLiveData<>();
    private final MutableLiveData<Map<SerbianRegion, RegionStats>> statsByRegion = new MutableLiveData<>();
    private final MutableLiveData<SerbianRegion> myRegion = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    private final RegionRepository repository = new RegionRepository();

    public LiveData<List<RegionMapPoint>> getMapPoints()          { return mapPoints; }
    public LiveData<Map<SerbianRegion, RegionStats>> getStatsByRegion() { return statsByRegion; }
    public LiveData<SerbianRegion> getMyRegion()                  { return myRegion; }
    public LiveData<String> getError()                            { return error; }
    public LiveData<Boolean> getLoading()                         { return loading; }

    public void setMyRegion(SerbianRegion region) {
        myRegion.setValue(region);
    }

    /** Učitaj sve tačke za mapu (svi registrovani igrači sa postavljenim regionom). */
    public void loadMapPoints() {
        loading.setValue(true);
        repository.loadMapPoints(new RegionRepository.MapPointsCallback() {
            @Override
            public void onSuccess(List<RegionMapPoint> points) {
                loading.setValue(false);
                mapPoints.setValue(points);
            }

            @Override
            public void onError(String message) {
                loading.setValue(false);
                error.setValue(message);
            }
        });
    }

    /** Učitaj statistiku (aktivni / ukupno) za svaki region. */
    public void loadRegionStats() {
        repository.loadRegionStats(new RegionRepository.StatsCallback() {
            @Override
            public void onSuccess(Map<SerbianRegion, RegionStats> stats) {
                statsByRegion.setValue(stats);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }
}