package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.tournament.TournamentRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
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

    private com.google.firebase.firestore.ListenerRegistration finalListener;
    private boolean finalNavigated = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game_over, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvWinner = view.findViewById(R.id.tvGameOverWinner);
        TextView tvStars = view.findViewById(R.id.tvGameOverStars);
        TextView tvTokens = view.findViewById(R.id.tvGameOverTokens);
        TextView tvScores = view.findViewById(R.id.tvGameOverScores);
        Button btnPlayAgain = view.findViewById(R.id.btnPlayAgain);
        Button btnHome = view.findViewById(R.id.btnGameOverHome);

        Bundle args = getArguments();
        if (args != null) {
            boolean friendly = args.getBoolean("friendly", false);
            boolean won = args.getBoolean("won", false);
            int starsDelta = args.getInt("starsDelta", 0);
            int tokensGained = args.getInt("tokensGained", 0);
            int myScore = args.getInt("myTotalScore", 0);
            int oppScore = args.getInt("opponentTotalScore", 0);
            boolean tournament = args.getBoolean("tournament", false);
            String sessionId = args.getString("sessionId");
            boolean finalRound = sessionId != null && sessionId.endsWith("_F");

            String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;

            if (tournament && sessionId != null && uid != null) {
                boolean isFinal = sessionId.endsWith("_F");

                // NAPOMENA: nagrade (zvezde/tokeni) i eventualno kreiranje finalne
                // partije već su odrađeni u MatchFinalizationRepository.finalizeForMe()
                // (poziva se iz MojBrojViewModel čim runda završi, PRE nego što se
                // uopšte dođe na ovaj ekran). Ovde se više NE sme ponovo zvati
                // TournamentRepository.finishTournamentMatch() - to je bila stara/
                // paralelna implementacija koja je duplirala nagrade i trkala se sa
                // MatchFinalizationRepository oko kreiranja finalne sesije, što je i
                // pravilo da finale ponekad uopšte ne nastane (ili igrač dobije duplo
                // vise zvezda/tokena). Ovde samo čekamo da neko od klijenata (bilo koji
                // od 2 finalista) kreira finalSessionId u tournamentQueue dokumentu.
                
if (won && !isFinal) {
    listenForFinal(uid);
}
            }

            String winnerText;

            if (tournament && finalRound) {
                if (won) {
                    winnerText = "🏆 Ti si pobednik turnira!";
                } else {
                    winnerText = "Kraj turnira.\nDrugo mesto u finalu!";
                }

                new AlertDialog.Builder(requireContext())
                        .setTitle("Turnir završen")
                        .setMessage(won
                                ? "Bravo! Ti si pobednik turnira! 🏆"
                                : "Kraj turnira. Stigao/la si do finala!")
                        .setPositiveButton("Super", null)
                        .show();

            } else {
                winnerText = won ? "Pobijedio/la si!" : "Izgubio/la si.";
            }
            if (friendly) {
                winnerText += "\n(Prijateljska partija - bez zvezda)";
            }
            tvWinner.setText(winnerText);

            tvWinner.setScaleX(0.5f);
            tvWinner.setScaleY(0.5f);
            tvWinner.setAlpha(0f);
            tvWinner.animate()
                    .alpha(1f)
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(500)
                    .withEndAction(() ->
                            tvWinner.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(300)
                                    .start())
                    .start();

// Rezultat
tvScores.setText(
        "Tvoj ukupni skor: " + myScore +
                " | Protivnik: " + oppScore
);

            boolean shouldShowRewards = !friendly && (!tournament || finalRound || won);
            if (!shouldShowRewards) {
                tvStars.setVisibility(View.GONE);
                tvTokens.setVisibility(View.GONE);
            } else {
                tvStars.setVisibility(View.VISIBLE);
                tvStars.setText(starsDelta >= 0
                        ? "Zvezde: +" + starsDelta
                        : "Zvezde: " + starsDelta);

                if (tokensGained > 0) {
                    tvTokens.setVisibility(View.VISIBLE);
                    tvTokens.setText("Zaradio/la si " + tokensGained + " token(a)!");
                } else {
                    tvTokens.setVisibility(View.GONE);
                }
            }
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean isGuest = user != null && user.isAnonymous();
        if (isGuest) {
            btnPlayAgain.setText("Registruj se za jos partija");
        }

        btnPlayAgain.setOnClickListener(v -> checkAndOpenFinalOrHome());
        btnHome.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_gameover_to_home));
    }

    private void listenForFinal(String uid) {
        if (finalListener != null) {
            finalListener.remove();
            finalListener = null;
        }

        finalListener = FirebaseFirestore.getInstance()
                .collection("tournamentQueue")
                .document(uid)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;

                    String status = doc.getString("status");
                    String sessionId = doc.getString("sessionId");

                    if ("matched".equals(status) && sessionId != null && sessionId.endsWith("_F")) {
                        openFinal(sessionId);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (finalListener != null) {
            finalListener.remove();
            finalListener = null;
        }
    }

    private void openFinal(String finalSessionId) {
        if (!isAdded() || finalNavigated) return;
        finalNavigated = true;

        if (finalListener != null) {
            finalListener.remove();
            finalListener = null;
        }

        FirebaseFirestore.getInstance()
                .collection("gameSessions")
                .document(finalSessionId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;

                    String currentUid = FirebaseAuth.getInstance()
                            .getCurrentUser()
                            .getUid();

                    String player1Uid = doc.getString("player1Uid");

                    Bundle args = new Bundle();
                    args.putString("sessionId", finalSessionId);
                    args.putBoolean("tournament", true);
                    args.putBoolean("isHost", currentUid.equals(player1Uid));
                    args.putString("hostUid", player1Uid);

                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_gameover_to_koZnaZna, args);
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("TOURNAMENT", "Ne mogu da otvorim finale", e)
                );
    }

    private void checkAndOpenFinalOrHome() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_gameover_to_home);
            return;
        }

        String uid = user.getUid();
        FirebaseFirestore.getInstance()
                .collection("tournamentQueue")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;

                    if (doc.exists()) {
                        String sessionId = doc.getString("sessionId");
                        if (sessionId != null && sessionId.endsWith("_F")) {
                            FirebaseFirestore.getInstance()
                                    .collection("gameSessions")
                                    .document(sessionId)
                                    .get()
                                    .addOnSuccessListener(gameDoc -> {
                                        if (!isAdded()) return;

                                        String currentUid = FirebaseAuth.getInstance()
                                                .getCurrentUser()
                                                .getUid();

                                        String player1Uid = gameDoc.getString("player1Uid");

                                        Bundle args = new Bundle();
                                        args.putString("sessionId", sessionId);
                                        args.putBoolean("tournament", true);
                                        args.putBoolean("isHost", currentUid.equals(player1Uid));
                                        args.putString("hostUid", player1Uid);

                                        NavHostFragment.findNavController(this)
                                                .navigate(R.id.action_gameover_to_koZnaZna, args);
                                    });

                            return;
                        }
                    }

                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_gameover_to_home);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_gameover_to_home);
                });
    }
}
