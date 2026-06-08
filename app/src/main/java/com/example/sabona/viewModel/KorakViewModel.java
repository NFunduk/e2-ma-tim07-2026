package com.example.sabona.viewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.sabona.model.KorakGame;
import com.example.sabona.repository.KorakRepository;

import java.util.List;

public class KorakViewModel extends ViewModel {

    public enum Phase { LOADING, MAIN, BONUS, ROUND_END, GAME_OVER }

    private final MutableLiveData<Phase>   phase          = new MutableLiveData<>();
    private final MutableLiveData<String>  infoText       = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentPoints  = new MutableLiveData<>();
    private final MutableLiveData<Integer> stepsRevealed  = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> error          = new MutableLiveData<>();
    private final MutableLiveData<int[]>   finalScores    = new MutableLiveData<>();

    private List<KorakGame> games;
    private int round         = 0;   // indeks u listi (0, 1)
    private int activePlayer  = 1;   // ko igra ovu rundu
    private int player1Score  = 0;
    private int player2Score  = 0;
    private int stepsShown    = 0;   // 0–7
    private boolean answered  = false;
    private int player1GuessedAtStep = 0; // 0 = nije pogodio, 1-7 = koji korak

    private final KorakRepository repo = new KorakRepository();

    public LiveData<Phase>   getPhase()         { return phase; }
    public LiveData<String>  getInfoText()       { return infoText; }
    public LiveData<Integer> getCurrentPoints()  { return currentPoints; }
    public LiveData<Integer> getStepsRevealed()  { return stepsRevealed; }
    public LiveData<Boolean> getError()          { return error; }
    public LiveData<int[]>   getFinalScores()    { return finalScores; }

    public int  getActivePlayer()  { return activePlayer; }
    public int  getRound()         { return round + 1; }  // 1-based za UI
    public KorakGame currentGame() { return games.get(round); }

    public int getPlayer1GuessedAtStep() { return player1GuessedAtStep; }
    public void loadGames() {
        phase.setValue(Phase.LOADING);
        repo.getGames(new KorakRepository.Callback() {
            @Override
            public void onSuccess(List<KorakGame> result) {
                // Uzmi samo 2 random igre
                java.util.Collections.shuffle(result);
                games = result.subList(0, Math.min(2, result.size()));
                startRound();
            }
            @Override
            public void onError(Exception e) {
                error.postValue(true);
            }
        });
    }

    public void startRound() {
        stepsShown = 1;
        answered   = false;
        phase.setValue(Phase.MAIN);
        stepsRevealed.setValue(1);
        infoText.setValue(
                "Igrač " + activePlayer + " igra rundu " + (round + 1) + "/2"
        );
        updatePointsDisplay();
    }

    /** Poziva Fragment svakih 10s iz tajmera */
    public void revealNextStep() {
        if (stepsShown >= 7 || answered) return;
        stepsShown++;
        stepsRevealed.postValue(stepsShown);
        updatePointsDisplay();
    }

    /**
     * @return true ako je odgovor tačan
     */
    public boolean submitAnswer(String guess) {
        if (answered) return false;
        if (!guess.equalsIgnoreCase(currentGame().answer)) return false;

        answered = true;

        if (phase.getValue() == Phase.MAIN) {
            // Aktivan igrač pogodio
            int pts = calculateMainPoints();
            addPoints(activePlayer, pts);
            if (activePlayer == 1) {
                player1GuessedAtStep = stepsShown; // stepsShown = koji korak je bio otvoren
            }
            infoText.postValue("Tačno! Igrač " + activePlayer + " osvaja " + pts + " bodova.");
        } else {
            // Protivnik pogodio u bonus fazi — uvek 5 bodova
            int opponent = (activePlayer == 1) ? 2 : 1;
            addPoints(opponent, 5);
            infoText.postValue("Bonus! Igrač " + opponent + " osvaja 5 bodova.");
        }

        phase.postValue(Phase.ROUND_END);
        return true;
    }

    /** Poziva Fragment kad istekne glavnih 70s — prelaz na bonus */
    public void onMainTimerFinished() {
        if (answered) return;
        int opponent = (activePlayer == 1) ? 2 : 1;
        infoText.postValue("Igrač " + activePlayer + " nije pogodio! "
                + "Igrač " + opponent + " ima 10s za bonus (5 bodova).");
        currentPoints.postValue(5);
        phase.postValue(Phase.BONUS);
    }

    /** Poziva Fragment kad istekne bonus 10s bez tačnog odgovora */
    public void onBonusTimerFinished() {
        if (answered) return;
        infoText.postValue("Niko nije pogodio! Rešenje: " + currentGame().answer);
        phase.postValue(Phase.ROUND_END);
    }

    /** Poziva Fragment posle 3s pauze na kraju runde */
    public void onRoundEndCountdownFinished() {
        if (round < games.size() - 1) {
            round++;
            activePlayer = 2;
            startRound();
        } else {
            finalScores.postValue(new int[]{player1Score, player2Score});
            phase.postValue(Phase.GAME_OVER);
        }
    }

    private int calculateMainPoints() {
        // Korak 1 = 20 bodova, svaki sledeći -2, min 0
        return Math.max(20 - (stepsShown - 1) * 2, 0);
    }

    private void updatePointsDisplay() {
        if (phase.getValue() == Phase.BONUS) {
            currentPoints.postValue(5);
        } else {
            // stepsShown=1 -> 20 bodova, stepsShown=2 -> 18, itd.
            currentPoints.postValue(Math.max(20 - (stepsShown - 1) * 2, 0));
        }
    }

    private void addPoints(int player, int points) {
        if (player == 1) player1Score += points;
        else             player2Score += points;
    }
}
