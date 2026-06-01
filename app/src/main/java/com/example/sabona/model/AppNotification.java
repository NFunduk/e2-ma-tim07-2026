package com.example.sabona.model;

import com.google.firebase.Timestamp;

public class AppNotification {

    private String id;
    private String title;
    private String message;
    private String channel;
    private String type;
    private String dataId;
    private Timestamp createdAt;

    private boolean read;
    private boolean actionable;
    private boolean handled;

    public AppNotification() {
    }

    public AppNotification(String title, String message, String channel,
                           String type, String dataId,
                           boolean read, boolean actionable) {
        this.title = title;
        this.message = message;
        this.channel = channel;
        this.type = type;
        this.dataId = dataId;
        this.read = read;
        this.actionable = actionable;
        this.handled = false;
        this.createdAt = Timestamp.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public String getMessage() {
        return message == null ? "" : message;
    }

    public String getChannel() {
        return channel == null ? "Ostalo" : channel;
    }

    public String getType() {
        return type == null ? "" : type;
    }

    public String getDataId() {
        return dataId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public boolean isActionable() {
        return actionable;
    }

    public boolean isHandled() {
        return handled;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    public String getTimeText() {
        if (createdAt == null) {
            return "upravo";
        }

        long diffMillis = System.currentTimeMillis() - createdAt.toDate().getTime();
        long diffMinutes = diffMillis / (1000 * 60);

        if (diffMinutes < 1) return "upravo";
        if (diffMinutes < 60) return "pre " + diffMinutes + " min";

        long diffHours = diffMinutes / 60;
        if (diffHours < 24) return "pre " + diffHours + " h";

        long diffDays = diffHours / 24;
        return "pre " + diffDays + " dana";
    }
}