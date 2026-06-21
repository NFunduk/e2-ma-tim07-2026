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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class GameOverFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game_over, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvWinner      = view.findViewById(R.id.tvGameOverWinner);
        TextView tvStars       = view.findViewById(R.id.tvGameOverStars);
        TextView tvTokens      = view.findViewById(R.id.tvGameOverTokens);
        TextView tvScores      = view.findViewById(R.id.tvGameOverScores);
        Button   btnPlayAgain  = view.findViewById(R.id.btnPlayAgain);
        Button   btnHome       = view.findViewById(R.id.btnGameOverHome);

        Bundle args = getArguments();
        if (args != null) {
            int  p1Score      = args.getInt("player1Score", 0);
            int  p2Score      = args.getInt("player2Score", 0);
            boolean friendly  = args.getBoolean("friendly", false);
            boolean won       = args.getBoolean("won", false);
            int  starsDelta   = args.getInt("starsDelta", 0);
            int  tokensGained = args.getInt("tokensGained", 0);
            int  myScore      = args.getInt("myTotalScore", 0);
            int  oppScore     = args.getInt("opponentTotalScore", 0);

            // Pobjednik
            String winnerText = won ? "Pobijedio/la si! 🏆" : "Izgubio/la si.";
            if (friendly) winnerText += "\n(Prijateljska partija — bez zvijezda)";
            tvWinner.setText(winnerText);

            // Ukupni bodovi
            tvScores.setText("Tvoj ukupni skor: " + myScore + "  |  Protivnik: " + oppScore);

            // Zvijezde i tokeni (samo za ozbiljne partije)
            if (friendly) {
                tvStars.setVisibility(View.GONE);
                tvTokens.setVisibility(View.GONE);
            } else {
                tvStars.setVisibility(View.VISIBLE);
                tvTokens.setVisibility(View.VISIBLE);

                String starsText = starsDelta >= 0
                        ? "Zvijezde: +" + starsDelta + " ⭐"
                        : "Zvijezde: " + starsDelta + " ⭐";
                tvStars.setText(starsText);

                if (tokensGained > 0) {
                    tvTokens.setText("Zaradio/la si " + tokensGained + " token(a)! 🎫");
                    tvTokens.setVisibility(View.VISIBLE);
                } else {
                    tvTokens.setVisibility(View.GONE);
                }
            }
        }

        // Gost (anonimni igrač) ne može ponovo igrati — nema tokene ni nalog
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean isGuest = user != null && user.isAnonymous();

        if (isGuest) {
            btnPlayAgain.setText("Registruj se za još partija");
            btnPlayAgain.setOnClickListener(v ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_gameover_to_home));
        } else {
            btnPlayAgain.setOnClickListener(v ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.action_gameover_to_home));
        }

        btnHome.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_gameover_to_home));
    }
}