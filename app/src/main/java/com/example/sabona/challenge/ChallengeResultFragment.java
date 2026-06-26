package com.example.sabona.challenge;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ChallengeResultFragment extends Fragment {

    private ListenerRegistration challengeListener;
    private String myUid = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_challenge_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String challengeId = args != null ? args.getString("challengeId", "") : "";
        int myScore = args != null ? args.getInt("myScore", 0) : 0;

        TextView tvTitle = view.findViewById(R.id.tvChallengeResultTitle);
        TextView tvMyScore = view.findViewById(R.id.tvMyScore);
        TextView tvReward = view.findViewById(R.id.tvReward);
        LinearLayout layoutResults = view.findViewById(R.id.layoutResults);

        tvTitle.setText("Rezultat izazova");
        tvMyScore.setText("Tvoj skor: " + myScore + " bodova");

        view.findViewById(R.id.btnBackHome).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_challengeResult_to_home));

        if (challengeId.isEmpty()) {
            showWaiting(null, layoutResults, tvReward);
            return;
        }

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        ChallengeRepository repo = new ChallengeRepository();
        repo.submitScore(challengeId, myUid, myScore, new ChallengeRepository.Callback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                listenForFinalResults(repo, challengeId, layoutResults, tvReward);
            }

            @Override
            public void onError(String msg) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                listenForFinalResults(repo, challengeId, layoutResults, tvReward);
            }
        });
    }

    private void listenForFinalResults(ChallengeRepository repo,
                                       String challengeId,
                                       LinearLayout layoutResults,
                                       TextView tvReward) {
        if (challengeListener != null) challengeListener.remove();
        challengeListener = repo.listenChallenge(challengeId, new ChallengeRepository.ChallengeCallback() {
            @Override
            public void onSuccess(Challenge ch) {
                if (!isAdded()) return;
                if (ch.isFinished()) {
                    showResults(ch, layoutResults, tvReward);
                } else {
                    showWaiting(ch, layoutResults, tvReward);
                }
            }

            @Override
            public void onError(String msg) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWaiting(@Nullable Challenge ch, LinearLayout layout, TextView tvReward) {
        layout.removeAllViews();
        tvReward.setVisibility(View.VISIBLE);

        int finished = ch != null && ch.getSubmittedUids() != null ? ch.getSubmittedUids().size() : 0;
        int total = ch != null ? ch.getParticipantCount() : 0;

        TextView row = new TextView(requireContext());
        row.setTextSize(15);
        row.setTextColor(0xFF0B1957);
        row.setPadding(0, dp(8), 0, dp(8));
        if (ch == null || total == 0) {
            row.setText("Cekanje rezultata izazova...");
        } else {
            row.setText("Cekanje da svi igraci zavrse: " + finished + "/" + total);
        }
        layout.addView(row);

        tvReward.setText("Rang lista i nagrade bice prikazani kada svi prijavljeni igraci zavrse partiju.");
    }

    private void showResults(Challenge ch, LinearLayout layout, TextView tvReward) {
        layout.removeAllViews();

        List<String> names = ch.getParticipantUsernames();
        List<String> uids = ch.getParticipantUids();
        List<Integer> scores = ch.getParticipantScores();

        if (names == null || uids == null || scores == null || uids.isEmpty()) {
            showFinishedFallback(layout, tvReward);
            return;
        }

        List<int[]> ranked = new ArrayList<>();
        for (int i = 0; i < uids.size(); i++) {
            int score = i < scores.size() ? scores.get(i) : 0;
            ranked.add(new int[]{i, score});
        }
        ranked.sort((a, b) -> b[1] - a[1]);

        String[] medals = {"1.", "2.", "3.", "4."};
        int totalStars = ch.getStarsWager() * ch.getParticipantCount();
        int totalTokens = ch.getTokensWager() * ch.getParticipantCount();

        for (int r = 0; r < ranked.size(); r++) {
            int idx = ranked.get(r)[0];
            int score = ranked.get(r)[1];
            String name = idx < names.size() ? names.get(idx) : "?";
            String uid = idx < uids.size() ? uids.get(idx) : "";
            String medal = r < medals.length ? medals[r] : (r + 1) + ".";

            TextView row = new TextView(requireContext());
            row.setTextSize(15);
            row.setPadding(0, dp(6), 0, dp(6));
            row.setTextColor(uid.equals(myUid) ? 0xFF426BC2 : 0xFF0B1957);
            row.setTypeface(null, uid.equals(myUid)
                    ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);

            String rewardHint = "";
            if (r == 0) {
                rewardHint = " -> +" + (int) (totalStars * 0.75)
                        + " zvezda, +" + (int) (totalTokens * 0.75) + " tokena";
            } else if (r == 1) {
                rewardHint = " -> ulog vracen";
            }

            row.setText(medal + "  " + name + " - " + score + " bod." + rewardHint);
            layout.addView(row);

            if (uid.equals(myUid)) {
                tvReward.setVisibility(View.VISIBLE);
                if (r == 0) {
                    tvReward.setText("Pobedio/la si. Dobijas "
                            + (int) (totalStars * 0.75) + " zvezda i "
                            + (int) (totalTokens * 0.75) + " tokena.");
                } else if (r == 1) {
                    tvReward.setText("Drugo mesto: vracen ti je ulog od "
                            + ch.getStarsWager() + " zvezda i "
                            + ch.getTokensWager() + " tokena.");
                } else {
                    tvReward.setText("Izazov je zavrsen. Ovaj put gubis ulog.");
                }
            }

            if (r < ranked.size() - 1) {
                View sep = new View(requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                lp.setMargins(0, dp(4), 0, dp(4));
                sep.setLayoutParams(lp);
                sep.setBackgroundColor(0xFFEEEEEE);
                layout.addView(sep);
            }
        }
    }

    private void showFinishedFallback(LinearLayout layout, TextView tvReward) {
        TextView row = new TextView(requireContext());
        row.setTextSize(15);
        row.setTextColor(0xFF0B1957);
        row.setPadding(0, dp(8), 0, dp(8));
        row.setText("Izazov je zavrsen. Rezultati trenutno nisu dostupni.");
        layout.addView(row);

        tvReward.setVisibility(View.VISIBLE);
        tvReward.setText("Izazov je zavrsen.");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (challengeListener != null) {
            challengeListener.remove();
            challengeListener = null;
        }
    }

    private int dp(int dp) {
        float d = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }
}
