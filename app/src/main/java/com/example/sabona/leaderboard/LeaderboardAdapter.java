package com.example.sabona.leaderboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.R;
import com.example.sabona.league.League;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder> {

    private final List<LeaderboardEntry> entries = new ArrayList<>();
    private String currentUid = "";

    public LeaderboardAdapter() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    public void submitList(List<LeaderboardEntry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LeaderboardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_entry, parent, false);
        return new LeaderboardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderboardViewHolder holder, int position) {
        LeaderboardEntry entry = entries.get(position);

        int rank = position + 1;
        holder.tvRank.setText(String.valueOf(rank));
        holder.tvUsername.setText(entry.getUsername());
        holder.tvStars.setText(entry.getStars() + " ★");
        holder.tvGamesPlayed.setText(entry.getGamesPlayed() + " part.");

        League league = League.fromIndex(entry.getLeague());
        holder.tvLeague.setText(league.displayName);

        int iconResId = holder.itemView.getContext().getResources().getIdentifier(
                league.iconResName,
                "drawable",
                holder.itemView.getContext().getPackageName()
        );

        if (iconResId != 0) {
            holder.imgLeague.setImageResource(iconResId);
        } else {
            holder.imgLeague.setImageResource(R.drawable.star);
        }

        if (entry.getUserId() != null && entry.getUserId().equals(currentUid)) {
            holder.itemView.setBackgroundResource(R.drawable.leaderboard_current_user_bg);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.leaderboard_item_bg);
        }

        if (rank == 1) {
            holder.tvRank.setText("🥇");
        } else if (rank == 2) {
            holder.tvRank.setText("🥈");
        } else if (rank == 3) {
            holder.tvRank.setText("🥉");
        }
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class LeaderboardViewHolder extends RecyclerView.ViewHolder {

        TextView tvRank, tvUsername, tvLeague, tvStars, tvGamesPlayed;
        ImageView imgLeague;

        public LeaderboardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvLeague = itemView.findViewById(R.id.tvLeague);
            tvStars = itemView.findViewById(R.id.tvStars);
            tvGamesPlayed = itemView.findViewById(R.id.tvGamesPlayed);
            imgLeague = itemView.findViewById(R.id.imgLeague);
        }
    }
}