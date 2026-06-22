package com.example.sabona.challenge;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.R;

import java.util.ArrayList;
import java.util.List;

public class ChallengeListAdapter extends RecyclerView.Adapter<ChallengeListAdapter.VH> {

    public interface OnJoinListener { void onJoin(Challenge challenge); }

    private List<Challenge> items = new ArrayList<>();
    private String currentUid;
    private OnJoinListener joinListener;

    public ChallengeListAdapter(String currentUid, OnJoinListener joinListener) {
        this.currentUid   = currentUid;
        this.joinListener = joinListener;
    }

    public void setItems(List<Challenge> list) {
        this.items = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_challenge, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Challenge ch = items.get(pos);
        boolean isParticipant = ch.getParticipantUids() != null
                && ch.getParticipantUids().contains(currentUid);
        boolean isFinished = ch.isFinished();
        boolean isFull     = ch.isFull();

        h.tvCreator.setText("⚔️ " + ch.getCreatorUsername());
        h.tvWager.setText("Ulog: " + ch.getStarsWager() + " ⭐  +  " + ch.getTokensWager() + " 🪙");

        // Status badge
        if (isFinished) {
            h.tvStatus.setText("Završeno");
            h.tvStatus.setBackgroundResource(R.drawable.chat_bubble_received_bg);
        } else if (isFull) {
            h.tvStatus.setText("U toku");
            h.tvStatus.setBackgroundResource(R.drawable.chat_bubble_sent_bg);
        } else {
            h.tvStatus.setText("Otvoreno · " + ch.getParticipantCount() + "/4");
            h.tvStatus.setBackgroundResource(R.drawable.chat_bubble_sent_bg);
        }

        // Lista učesnika
        if (ch.getParticipantUsernames() != null && !ch.getParticipantUsernames().isEmpty()) {
            h.tvParticipants.setText("Igrači: " + String.join(", ", ch.getParticipantUsernames()));
        } else {
            h.tvParticipants.setText("Igrači: -");
        }

        // Rezultati kad je gotovo
        if (isFinished && ch.getParticipantScores() != null
                && !ch.getParticipantScores().isEmpty()) {
            h.tvResults.setVisibility(View.VISIBLE);

            // Nađi pobednika (najveći skor)
            List<Integer> scores  = ch.getParticipantScores();
            List<String>  names   = ch.getParticipantUsernames();
            StringBuilder sb = new StringBuilder("Rezultati:\n");
            // Sortiraj kopiju po skoru
            List<int[]> ranked = new ArrayList<>();
            for (int i = 0; i < scores.size(); i++) ranked.add(new int[]{i, scores.get(i)});
            ranked.sort((a, b) -> b[1] - a[1]);
            String[] medals = {"🥇", "🥈", "🥉", "4."};
            for (int r = 0; r < ranked.size(); r++) {
                int idx = ranked.get(r)[0];
                String medal = r < medals.length ? medals[r] : (r+1)+".";
                sb.append(medal).append(" ")
                        .append(idx < names.size() ? names.get(idx) : "?")
                        .append(" — ")
                        .append(ranked.get(r)[1])
                        .append(" bodova\n");
            }
            h.tvResults.setText(sb.toString().trim());
        } else {
            h.tvResults.setVisibility(View.GONE);
        }

        // Dugme
        if (isFinished || isParticipant || isFull) {
            h.btnJoin.setVisibility(View.GONE);
        } else {
            h.btnJoin.setVisibility(View.VISIBLE);
            h.btnJoin.setOnClickListener(v -> {
                if (joinListener != null) joinListener.onJoin(ch);
            });
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCreator, tvStatus, tvWager, tvParticipants, tvResults;
        Button btnJoin;
        VH(@NonNull View v) {
            super(v);
            tvCreator      = v.findViewById(R.id.tvCreator);
            tvStatus       = v.findViewById(R.id.tvStatus);
            tvWager        = v.findViewById(R.id.tvWager);
            tvParticipants = v.findViewById(R.id.tvParticipants);
            tvResults      = v.findViewById(R.id.tvResults);
            btnJoin        = v.findViewById(R.id.btnJoin);
        }
    }
}