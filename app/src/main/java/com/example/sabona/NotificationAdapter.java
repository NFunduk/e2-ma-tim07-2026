package com.example.sabona;

import android.content.Context;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.model.AppNotification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final Context context;
    private List<AppNotification> notifications;
    private final NotificationActionListener listener;

    // Aktivni countdown tajmeri po identitetu view-a — otkazuju se pri recikliranju
    private final Map<Integer, CountDownTimer> activeTimers = new HashMap<>();

    private static final long GAME_INVITE_TIMEOUT_MS = 10_000L;

    public NotificationAdapter(Context context,
                               List<AppNotification> notifications,
                               NotificationActionListener listener) {
        this.context = context;
        this.notifications = notifications;
        this.listener = listener;
    }

    public void setNotifications(List<AppNotification> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        AppNotification notification = notifications.get(position);

        holder.title.setText(notification.getTitle());
        holder.message.setText(notification.getMessage());

        String status = notification.isRead() ? "Pročitano" : "Nepročitano";
        holder.status.setText(notification.getChannel() + " · " + status + " · " + notification.getTimeText());

        if (notification.isRead()) {
            holder.card.setBackgroundResource(R.drawable.notification_read_bg);
        } else {
            holder.card.setBackgroundResource(R.drawable.notification_unread_bg);
        }

        if (notification.isActionable() && !notification.isHandled()) {
            holder.actionsLayout.setVisibility(View.VISIBLE);
        } else {
            holder.actionsLayout.setVisibility(View.GONE);
        }

        if (notification.isHandled()) {
            holder.actionStatus.setVisibility(View.VISIBLE);
            holder.actionStatus.setText("Na ovu notifikaciju je već odgovoreno.");
        } else {
            holder.actionStatus.setVisibility(View.GONE);
        }

        bindGameInviteCountdown(holder, notification);

        holder.card.setOnClickListener(v -> {
            if (!notification.isRead()) {
                listener.onMarkAsRead(notification);
            }
        });

        holder.btnAccept.setOnClickListener(v -> listener.onAccept(notification));
        holder.btnReject.setOnClickListener(v -> listener.onReject(notification));
    }

    @Override
    public int getItemCount() {
        return notifications == null ? 0 : notifications.size();
    }

    /**
     * Za notifikacije tipa "friend_game_invite" koje još čekaju odgovor (pending),
     * prikazuje odbrojavanje preostalog vremena (10s od trenutka slanja poziva)
     * i automatski sakriva dugmiće Prihvati/Odbij kad vreme istekne — stvarno
     * odbijanje (status u Firestore-u) se i dalje obavlja na strani pošiljaoca.
     */
    private void bindGameInviteCountdown(NotificationViewHolder holder, AppNotification notification) {
        // Otkaži prethodni tajmer vezan za ovaj view (recikliranje RecyclerView-a)
        int viewKey = System.identityHashCode(holder.itemView);
        CountDownTimer previous = activeTimers.remove(viewKey);
        if (previous != null) previous.cancel();

        boolean isPendingGameInvite = "friend_game_invite".equals(notification.getType())
                && notification.isActionable()
                && !notification.isHandled();

        if (!isPendingGameInvite || notification.getCreatedAt() == null) {
            holder.tvInviteCountdown.setVisibility(View.GONE);
            return;
        }

        long elapsed = System.currentTimeMillis() - notification.getCreatedAt().toDate().getTime();
        long remaining = GAME_INVITE_TIMEOUT_MS - elapsed;

        if (remaining <= 0) {
            // Vreme je već isteklo — ne dozvoljavamo dalju akciju iz UI-a
            holder.tvInviteCountdown.setVisibility(View.GONE);
            holder.actionsLayout.setVisibility(View.GONE);
            return;
        }

        holder.tvInviteCountdown.setVisibility(View.VISIBLE);
        holder.tvInviteCountdown.setText("Ističe za " + (remaining / 1000 + 1) + "s");

        CountDownTimer timer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                holder.tvInviteCountdown.setText(
                        "Ističe za " + (millisUntilFinished / 1000 + 1) + "s");
            }

            @Override
            public void onFinish() {
                holder.tvInviteCountdown.setVisibility(View.GONE);
                holder.actionsLayout.setVisibility(View.GONE);
            }
        };
        timer.start();
        activeTimers.put(viewKey, timer);
    }

    @Override
    public void onViewRecycled(@NonNull NotificationViewHolder holder) {
        super.onViewRecycled(holder);
        CountDownTimer timer = activeTimers.remove(System.identityHashCode(holder.itemView));
        if (timer != null) timer.cancel();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {

        LinearLayout card, actionsLayout;
        TextView title, message, status, actionStatus, tvInviteCountdown;
        Button btnAccept, btnReject;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);

            card = itemView.findViewById(R.id.notificationCard);
            title = itemView.findViewById(R.id.notificationTitle);
            message = itemView.findViewById(R.id.notificationMessage);
            status = itemView.findViewById(R.id.notificationStatus);
            actionsLayout = itemView.findViewById(R.id.actionsLayout);
            actionStatus = itemView.findViewById(R.id.actionStatus);
            tvInviteCountdown = itemView.findViewById(R.id.tvInviteCountdown);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
        }
    }

    public interface NotificationActionListener {
        void onMarkAsRead(AppNotification notification);
        void onAccept(AppNotification notification);
        void onReject(AppNotification notification);
    }
}