package com.example.sabona.friends;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.R;
import com.example.sabona.model.FriendUser;

import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.VH> {

    public static final int ACTION_INVITE = 1; // za listu prijatelja
    public static final int ACTION_ADD    = 2; // za rezultate pretrage

    public interface OnActionListener {
        void onAction(FriendUser user, int action);
    }

    private List<FriendUser>   items;
    private final OnActionListener listener;
    private final boolean          isSearchMode; // true = pokazuje dugme "Dodaj", false = "Igraj"

    public FriendsAdapter(List<FriendUser> items, OnActionListener listener, boolean isSearchMode) {
        this.items        = items;
        this.listener     = listener;
        this.isSearchMode = isSearchMode;
    }

    public void setItems(List<FriendUser> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FriendUser user = items.get(position);

        holder.tvUsername.setText(user.getUsername());
        holder.tvStars.setText(user.getStars() + " ⭐");
        holder.tvLeague.setText("Liga " + user.getLeague());

        if (isSearchMode) {
            holder.btnAction.setImageResource(R.drawable.outline_person_24); // dodaj ikona
            holder.btnAction.setContentDescription("Dodaj prijatelja");
            holder.btnAction.setOnClickListener(v -> listener.onAction(user, ACTION_ADD));
        } else {
            holder.btnAction.setImageResource(R.drawable.baseline_play_arrow_24); // igraj
            holder.btnAction.setContentDescription("Pozovi na partiju");
            holder.btnAction.setOnClickListener(v -> listener.onAction(user, ACTION_INVITE));
        }
    }

    @Override
    public int getItemCount() { return items != null ? items.size() : 0; }

    static class VH extends RecyclerView.ViewHolder {
        TextView    tvUsername, tvStars, tvLeague;
        ImageButton btnAction;

        VH(@NonNull View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tvFriendUsername);
            tvStars    = itemView.findViewById(R.id.tvFriendStars);
            tvLeague   = itemView.findViewById(R.id.tvFriendLeague);
            btnAction  = itemView.findViewById(R.id.btnFriendAction);
        }
    }
}