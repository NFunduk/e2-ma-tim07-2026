package com.example.sabona.friends;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
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
        holder.tvRank.setText(user.getMonthlyRank() > 0
                ? "#" + user.getMonthlyRank() + " mesečno"
                : "Bez ranga");

        holder.ivAvatar.setImageResource(resolveAvatar(user.getAvatarRes()));

        if (isSearchMode) {
            // U pretrazi ne prikazujemo online indikator/dugme za igru.
            holder.ivOnlineDot.setVisibility(View.GONE);
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setEnabled(true);
            holder.btnAction.setAlpha(1.0f);
            holder.btnAction.setImageResource(R.drawable.outline_person_24); // dodaj ikona
            holder.btnAction.setContentDescription("Dodaj prijatelja");
            holder.btnAction.setOnClickListener(v -> listener.onAction(user, ACTION_ADD));
        } else {
            holder.ivOnlineDot.setVisibility(View.VISIBLE);
            holder.ivOnlineDot.setImageResource(user.isOnline()
                    ? R.drawable.dot_online
                    : R.drawable.dot_offline);

            boolean available = user.isAvailableForGame();

            holder.btnAction.setImageResource(R.drawable.baseline_play_arrow_24); // igraj
            holder.btnAction.setEnabled(available);
            holder.btnAction.setAlpha(available ? 1.0f : 0.4f);
            holder.btnAction.setContentDescription(
                    available ? "Pozovi na partiju"
                            : (user.isInGame() ? "Prijatelj je u partiji" : "Prijatelj nije ulogovan"));
            holder.btnAction.setOnClickListener(v -> {
                if (available) listener.onAction(user, ACTION_INVITE);
            });
        }
    }

    @Override
    public int getItemCount() { return items != null ? items.size() : 0; }

    /** Mapira naziv avatara (sačuvan kao string u Firestore) u drawable resurs. */
    private int resolveAvatar(String avatarRes) {
        if (avatarRes == null) return R.drawable.outline_account_circle_24;
        switch (avatarRes) {
            case "gallery": return android.R.drawable.ic_menu_gallery;
            case "places":  return android.R.drawable.ic_menu_myplaces;
            case "compass": return android.R.drawable.ic_menu_compass;
            case "camera":  return android.R.drawable.ic_menu_camera;
            case "manage":  return android.R.drawable.ic_menu_manage;
            case "search":  return android.R.drawable.ic_menu_search;
            case "share":   return android.R.drawable.ic_menu_share;
            case "send":    return android.R.drawable.ic_menu_send;
            case "add":     return android.R.drawable.ic_menu_add;
            case "info":    return android.R.drawable.ic_menu_info_details;
            case "edit":    return android.R.drawable.ic_menu_edit;
            case "view":    return android.R.drawable.ic_menu_view;
            default:        return R.drawable.outline_account_circle_24;
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView    tvUsername, tvStars, tvLeague, tvRank;
        ImageView   ivAvatar, ivOnlineDot;
        ImageButton btnAction;

        VH(@NonNull View itemView) {
            super(itemView);
            tvUsername  = itemView.findViewById(R.id.tvFriendUsername);
            tvStars     = itemView.findViewById(R.id.tvFriendStars);
            tvLeague    = itemView.findViewById(R.id.tvFriendLeague);
            tvRank      = itemView.findViewById(R.id.tvFriendRank);
            ivAvatar    = itemView.findViewById(R.id.ivFriendAvatar);
            ivOnlineDot = itemView.findViewById(R.id.ivFriendOnlineDot);
            btnAction   = itemView.findViewById(R.id.btnFriendAction);
        }
    }
}