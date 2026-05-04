package com.example.sabona;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final Context context;
    private List<AppNotification> notifications;

    public NotificationAdapter(Context context, List<AppNotification> notifications) {
        this.context = context;
        this.notifications = notifications;
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
        holder.status.setText(notification.getChannel() + " · " + status + " · " + notification.getTime());

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
                notification.setRead(true);
                Toast.makeText(context, "Notifikacija je označena kao pročitana", Toast.LENGTH_SHORT).show();
                notifyItemChanged(position);
            } else {
                Toast.makeText(context, "Notifikacija je već pročitana", Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnAccept.setOnClickListener(v -> {
            notification.setRead(true);
            notification.setHandled(true);
            Toast.makeText(context, "Prihvatio/la si zahtev", Toast.LENGTH_SHORT).show();
            notifyItemChanged(position);
        });

        holder.btnReject.setOnClickListener(v -> {
            notification.setRead(true);
            notification.setHandled(true);
            Toast.makeText(context, "Odbio/la si zahtev", Toast.LENGTH_SHORT).show();
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
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
}