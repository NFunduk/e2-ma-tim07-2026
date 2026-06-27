package com.example.sabona.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.sabona.MainActivity;
import com.example.sabona.R;
import com.example.sabona.model.AppNotification;

public class NotificationHelper {

    public static final String CHANNEL_CHAT = "chat_channel";
    public static final String CHANNEL_RANKING = "ranking_channel";
    public static final String CHANNEL_REWARDS = "rewards_channel";
    public static final String CHANNEL_OTHER = "other_channel";

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);

            if (manager == null) return;

            NotificationChannel chat = new NotificationChannel(
                    CHANNEL_CHAT,
                    "Čet",
                    NotificationManager.IMPORTANCE_HIGH
            );
            chat.setDescription("Obaveštenja o porukama u četu");

            NotificationChannel ranking = new NotificationChannel(
                    CHANNEL_RANKING,
                    "Rangiranje",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ranking.setDescription("Obaveštenja o rang listama i ligama");

            NotificationChannel rewards = new NotificationChannel(
                    CHANNEL_REWARDS,
                    "Nagrade",
                    NotificationManager.IMPORTANCE_HIGH
            );
            rewards.setDescription("Obaveštenja o osvojenim nagradama");

            NotificationChannel other = new NotificationChannel(
                    CHANNEL_OTHER,
                    "Ostalo",
                    NotificationManager.IMPORTANCE_HIGH
            );
            other.setDescription("Pozivi za prijatelja, partije i ostalo");

            manager.createNotificationChannel(chat);
            manager.createNotificationChannel(ranking);
            manager.createNotificationChannel(rewards);
            manager.createNotificationChannel(other);
        }
    }

    public static String getChannelId(String channel) {
        if ("Čet".equals(channel)) {
            return CHANNEL_CHAT;
        } else if ("Rangiranje".equals(channel)) {
            return CHANNEL_RANKING;
        } else if ("Nagrade".equals(channel)) {
            return CHANNEL_REWARDS;
        } else {
            return CHANNEL_OTHER;
        }
    }

    public static void showNotification(Context context, AppNotification notification) {

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction("com.example.sabona.OPEN_NOTIFICATIONS");
        intent.putExtra("open_notifications", true);
        intent.putExtra("notification_id", notification.getId());
        intent.putExtra("notification_type", notification.getType());
        intent.putExtra("notification_message", notification.getMessage());
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int notificationId = notification.getId() != null && !notification.getId().isEmpty()
                ? notification.getId().hashCode()
                : (int) System.currentTimeMillis();

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, getChannelId(notification.getChannel()))
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(notification.getTitle())
                        .setContentText(notification.getMessage())
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(notification.getMessage()))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .setOnlyAlertOnce(false)
                        .setContentIntent(pendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManagerCompat.from(context)
                .notify(notificationId, builder.build());
    }
}
