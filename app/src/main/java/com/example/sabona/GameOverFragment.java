package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.league.LeagueChangeDialog;
import com.example.sabona.league.LeagueManager;
import com.example.sabona.league.LeagueRepository;
import com.example.sabona.repository.NotificationFactory;
import com.example.sabona.repository.NotificationRepository;

/**
 * Fragment koji se prikazuje na kraju svake partije.
 *
 * Prima argumente:
 *   - "winner"       : String  – tekst pobjednika
 *   - "player1Won"   : boolean – da li je lokalni igrač pobijedio
 *   - "totalScore"   : int     – ukupan broj bodova lokalnog igrača
 *   - "isFriendGame" : boolean – da li je u pitanju prijateljska partija
 *
 * Zvezde i liga se NE mijenjaju za prijateljske partije.
 */
public class GameOverFragment extends Fragment {

    private final LeagueRepository    leagueRepo    = new LeagueRepository();
    private final NotificationRepository notifRepo  = new NotificationRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game_over, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvWinner   = view.findViewById(R.id.tvGameOverWinner);
        Button btnPlayAgain = view.findViewById(R.id.btnPlayAgain);
        Button btnHome      = view.findViewById(R.id.btnGameOverHome);

        boolean player1Won   = false;
        int     totalScore   = 0;
        boolean isFriendGame = false;

        if (getArguments() != null) {
            String winnerText = getArguments().getString("winner", "");
            tvWinner.setText(winnerText);
            player1Won   = getArguments().getBoolean("player1Won", false);
            totalScore   = getArguments().getInt("totalScore", 0);
            isFriendGame = getArguments().getBoolean("isFriendGame", false);
        }

        // Ažuriraj zvezde i ligu (samo za regularne partije)
        if (!isFriendGame) {
            int starsDelta = calculateStarsDelta(player1Won, totalScore);
            applyStarsAndLeague(starsDelta);
        }

        // Navigacija
        btnPlayAgain.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_gameover_to_home));

        btnHome.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_gameover_to_home));
    }

    //  Izračun zvezda po specifikaciji

    /**
     * Pobjednik:  +10 + (score / 40) zvezda
     * Gubitnik:   -10 + (score / 40) zvezda  (ne ide ispod 0 – LeagueManager čuva)
     *
     * Primjer: pobijedio s 150 bodova → +10 + 3 = +13
     *          izgubio s 100 bodova   → -10 + 2 = -8
     */
    private int calculateStarsDelta(boolean won, int score) {
        int bonusStars = score / 40; // cjelobrojno dijeljenje
        if (won) {
            return 10 + bonusStars;
        } else {
            return -10 + bonusStars; // može biti negativno
        }
    }

    //  Primjena promjene u Firestoru + dijalog ako se liga promijeni

    private void applyStarsAndLeague(int starsDelta) {
        leagueRepo.applyStarChange(starsDelta, new LeagueRepository.LeagueCallback() {
            @Override
            public void onSuccess(LeagueManager.LeagueChangeResult result) {
                if (!isAdded()) return; // Fragment možda nije više priložen

                // Ako se liga promijenila → dijalog + notifikacija
                if (result.leagueChanged()) {
                    boolean promoted = result.promoted == Boolean.TRUE;
                    LeagueChangeDialog dialog = LeagueChangeDialog.newInstance(
                            result.oldLeague.displayName,
                            result.newLeague.displayName,
                            promoted
                    );
                    dialog.show(getChildFragmentManager(), "league_change");

                    // Sačuvaj notifikaciju u bazu
                    String msg = promoted
                            ? "Napredovao si u: " + result.newLeague.displayName
                            : "Spao si na: " + result.newLeague.displayName;
                    notifRepo.createNotification(
                            com.google.firebase.auth.FirebaseAuth.getInstance()
                                    .getCurrentUser().getUid(),
                            NotificationFactory.leagueChanged(result.newLeague.displayName)
                    );
                }
            }

            @Override
            public void onError(String message) {
                // Tiho loguj – ne prekidaj korisnikov doživljaj kraj partije
                android.util.Log.e("GameOver", "Greška pri ažuriranju zvezda: " + message);
            }
        });
    }
}