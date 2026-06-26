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

    public interface OnJoinListener {
        void onJoin(Challenge challenge);
        void onPlay(Challenge challenge);
    }

    private List<Challenge> items = new ArrayList<>();
    private final String currentUid;
    private final OnJoinListener joinListener;

    public ChallengeListAdapter(String currentUid, OnJoinListener joinListener) {
        this.currentUid = currentUid;
        this.joinListener = joinListener;
    }

    public void setItems(List<Challenge> list) {
        this.items = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
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
        boolean isFull = ch.isFull();
        boolean isInProgress = Challenge.STATUS_IN_PROGRESS.equals(ch.getStatus());
        boolean hasSubmitted = ch.hasSubmitted(currentUid);
        int participantCount = ch.getParticipantCount();
        int submittedCount = ch.getSubmittedUids() != null ? ch.getSubmittedUids().size() : 0;

        h.tvCreator.setText("Kreator: " + safe(ch.getCreatorUsername()));
        h.tvWager.setText("Ulog: " + ch.getStarsWager() + " zvezda + "
                + ch.getTokensWager() + " tokena");

        if (isFinished) {
            h.tvStatus.setText("Zavrseno");
            h.tvStatus.setBackgroundResource(R.drawable.chat_bubble_received_bg);
        } else if (isInProgress) {
            h.tvStatus.setText("U toku - " + submittedCount + "/" + participantCount + " zavrsilo");
            h.tvStatus.setBackgroundResource(R.drawable.chat_bubble_sent_bg);
        } else {
            h.tvStatus.setText("Otvoreno - " + participantCount + "/4");
            h.tvStatus.setBackgroundResource(R.drawable.chat_bubble_sent_bg);
        }

        if (ch.getParticipantUsernames() != null && !ch.getParticipantUsernames().isEmpty()) {
            h.tvParticipants.setText("Igraci: " + String.join(", ", ch.getParticipantUsernames()));
        } else {
            h.tvParticipants.setText("Igraci: -");
        }

        h.tvResults.setVisibility(View.VISIBLE);
        if (isFinished) {
            h.tvResults.setText(buildFinishedText(ch));
        } else if (hasSubmitted) {
            h.tvResults.setText("Zavrsio si svoju partiju. Ceka se da ostali igraci zavrse: "
                    + submittedCount + "/" + participantCount + ".");
        } else if (isInProgress) {
            h.tvResults.setText("Izazov je zapocet. Odigraj svoju partiju da bi rezultat usao u rang listu.");
        } else {
            h.tvResults.setText("Izazov je otvoren. Igraci mogu da se prijave ili zapocnu svoju partiju.");
        }

        if (isFinished || hasSubmitted || (!isParticipant && isFull)) {
            h.btnJoin.setVisibility(View.GONE);
        } else {
            h.btnJoin.setVisibility(View.VISIBLE);
            h.btnJoin.setText(isParticipant ? "Igraj" : "Pridruzi se");
            h.btnJoin.setOnClickListener(v -> {
                if (joinListener == null) return;
                if (isParticipant) joinListener.onPlay(ch);
                else joinListener.onJoin(ch);
            });
        }
    }

    private String buildFinishedText(Challenge ch) {
        List<Integer> scores = ch.getParticipantScores();
        List<String> names = ch.getParticipantUsernames();
        if (scores == null || scores.isEmpty()) {
            return "Izazov je zavrsen. Nema rezultata za prikaz.";
        }

        StringBuilder sb = new StringBuilder("Izazov je zavrsen.\nRezultati:\n");
        List<int[]> ranked = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) ranked.add(new int[]{i, scores.get(i)});
        ranked.sort((a, b) -> b[1] - a[1]);

        for (int r = 0; r < ranked.size(); r++) {
            int idx = ranked.get(r)[0];
            sb.append(r + 1)
                    .append(". ")
                    .append(idx < names.size() ? names.get(idx) : "?")
                    .append(" - ")
                    .append(ranked.get(r)[1])
                    .append(" bodova");
            if (r == 0) sb.append(" - pobednik");
            if (r == 1) sb.append(" - ulog vracen");
            if (r < ranked.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private String safe(String value) {
        return value != null && !value.trim().isEmpty() ? value : "Igrac";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCreator, tvStatus, tvWager, tvParticipants, tvResults;
        Button btnJoin;

        VH(@NonNull View v) {
            super(v);
            tvCreator = v.findViewById(R.id.tvCreator);
            tvStatus = v.findViewById(R.id.tvStatus);
            tvWager = v.findViewById(R.id.tvWager);
            tvParticipants = v.findViewById(R.id.tvParticipants);
            tvResults = v.findViewById(R.id.tvResults);
            btnJoin = v.findViewById(R.id.btnJoin);
        }
    }
}
