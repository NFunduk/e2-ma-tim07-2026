package com.example.sabona.challenge;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ChallengeResultFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_challenge_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String challengeId = args != null ? args.getString("challengeId", "") : "";
        int myScore = args != null ? args.getInt("myScore", 0) : 0;

        TextView tvMyScore   = view.findViewById(R.id.tvMyScore);
        TextView tvReward    = view.findViewById(R.id.tvReward);
        LinearLayout layoutResults = view.findViewById(R.id.layoutResults);

        tvMyScore.setText("Tvoj skor: " + myScore + " bodova");

        view.findViewById(R.id.btnBackHome).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_challengeResult_to_home));

        if (challengeId.isEmpty()) return;

        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        // Upiši skor i učitaj finalne rezultate
        ChallengeRepository repo = new ChallengeRepository();
        repo.submitScore(challengeId, myUid, myScore, new ChallengeRepository.Callback() {
            @Override
            public void onSuccess() {
                // Učitaj ažurirani challenge da prikažemo rang listu
                FirebaseFirestore.getInstance()
                        .collection("challenges")
                        .document(challengeId)
                        .get()
                        .addOnSuccessListener(snap -> {
                            if (!isAdded() || snap == null) return;
                            Challenge ch = snap.toObject(Challenge.class);
                            if (ch == null) return;
                            ch.setId(snap.getId());
                            showResults(ch, myUid, layoutResults, tvReward);
                        });
            }
            @Override
            public void onError(String msg) {
                // Tiho — prikaži bar naš skor
            }
        });
    }

    private void showResults(Challenge ch, String myUid,
                             LinearLayout layout, TextView tvReward) {
        if (!isAdded()) return;
        layout.removeAllViews();

        List<String>  names  = ch.getParticipantUsernames();
        List<String>  uids   = ch.getParticipantUids();
        List<Integer> scores = ch.getParticipantScores();

        if (names == null || scores == null) return;

        // Sortiraj po skoru
        List<int[]> ranked = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) ranked.add(new int[]{i, scores.get(i)});
        ranked.sort((a, b) -> b[1] - a[1]);

        String[] medals = {"🥇", "🥈", "🥉", "4."};
        int totalStars  = ch.getStarsWager()  * ch.getParticipantCount();
        int totalTokens = ch.getTokensWager() * ch.getParticipantCount();

        for (int r = 0; r < ranked.size(); r++) {
            int idx   = ranked.get(r)[0];
            int score = ranked.get(r)[1];
            String name  = idx < names.size() ? names.get(idx) : "?";
            String uid   = idx < uids.size()  ? uids.get(idx)  : "";
            String medal = r < medals.length  ? medals[r]      : (r + 1) + ".";

            TextView row = new TextView(requireContext());
            row.setTextSize(15);
            row.setPadding(0, dp(6), 0, dp(6));
            row.setTextColor(uid.equals(myUid) ? 0xFF426BC2 : 0xFF0B1957);
            row.setTypeface(null, uid.equals(myUid)
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

            String rewardHint = "";
            if (r == 0) rewardHint = " → +" + (int)(totalStars * 0.75) + "⭐ +" + (int)(totalTokens * 0.75) + "🪙";
            else if (r == 1) rewardHint = " → ulog vraćen";

            row.setText(medal + "  " + name + "  —  " + score + " bod." + rewardHint);
            layout.addView(row);

            // Separator
            if (r < ranked.size() - 1) {
                View sep = new View(requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                lp.setMargins(0, dp(4), 0, dp(4));
                sep.setLayoutParams(lp);
                sep.setBackgroundColor(0xFFEEEEEE);
                layout.addView(sep);
            }

            // Poruka za mene
            if (uid.equals(myUid)) {
                tvReward.setVisibility(View.VISIBLE);
                if (r == 0) {
                    tvReward.setText("🏆 Pobedio/la si! Nagrađen/a si sa " +
                            (int)(totalStars * 0.75) + " zvezda i " +
                            (int)(totalTokens * 0.75) + " tokena!");
                } else if (r == 1) {
                    tvReward.setText("Vratio/la si uloženo — " +
                            ch.getStarsWager() + " zvezda i " + ch.getTokensWager() + " tokena.");
                } else {
                    tvReward.setText("Izgubio/la si ulog ovog puta. Pokušaj ponovo! 💪");
                    tvReward.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private int dp(int dp) {
        float d = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }
}