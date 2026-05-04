package com.example.sabona;

public class AppNotification {

    private String title;
    private String message;
    private String channel;
    private String time;
    private boolean read;
    private boolean actionable;
    private boolean handled;
    private String type;

    public AppNotification(String title, String message, String channel, String time,
                           boolean read, boolean actionable, String type) {
        this.title = title;
        this.message = message;
        this.channel = channel;
        this.time = time;
        this.read = read;
        this.actionable = actionable;
        this.handled = false;
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getChannel() {
        return channel;
    }

    public String getTime() {
        return time;
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

    public String getType() {
        return type;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }
}