package com.example.sabona;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.model.AppNotification;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final Context context;
    private List<AppNotification> notifications;
    private final NotificationActionListener listener;

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

    static class NotificationViewHolder extends RecyclerView.ViewHolder {

        LinearLayout card, actionsLayout;
        TextView title, message, status, actionStatus;
        Button btnAccept, btnReject;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);

            card = itemView.findViewById(R.id.notificationCard);
            title = itemView.findViewById(R.id.notificationTitle);
            message = itemView.findViewById(R.id.notificationMessage);
            status = itemView.findViewById(R.id.notificationStatus);
            actionsLayout = itemView.findViewById(R.id.actionsLayout);
            actionStatus = itemView.findViewById(R.id.actionStatus);
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