package com.example.sabona.model;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Jedna poruka u regionalnom četu (tačka 8 specifikacije).
 *
 * Čuva se u Firestore-u na putanji:
 *  regionChats/{region}/messages/{messageId}
 */
public class ChatMessage {

    private String senderId;
    private String senderName;
    private String text;
    private Timestamp timestamp;

    private String id;

    public ChatMessage() {
    }

    public ChatMessage(String senderId, String senderName, String text) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = Timestamp.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName == null ? "Igrač" : senderName;
    }

    public String getText() {
        return text == null ? "" : text;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    /** Datum i vreme slanja, formatirano za prikaz u baloniću poruke. */
    public String getTimeText() {
        if (timestamp == null) {
            return "";
        }
        return new SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault())
                .format(timestamp.toDate());
    }
}