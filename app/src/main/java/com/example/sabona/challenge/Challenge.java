package com.example.sabona.challenge;

import java.util.ArrayList;
import java.util.List;

public class Challenge {

    // status vrednosti
    public static final String STATUS_OPEN       = "open";       // čeka igrače
    public static final String STATUS_IN_PROGRESS = "inProgress"; // svi ušli, igraju se
    public static final String STATUS_FINISHED   = "finished";   // završeno

    private String id;
    private String creatorUid;
    private String creatorUsername;
    private String region;
    private int starsWager;    // ulog zvezda (1-10)
    private int tokensWager;   // ulog tokena (1-2)
    private String status;
    private List<String> participantUids;       // uid-ovi svih igrača koji su ušli
    private List<String> participantUsernames;  // username-ovi (za prikaz)
    private List<Integer> participantScores;    // finalni skorovi (paralelno sa uid listom)
    private long createdAt;

    public Challenge() {} // Firestore zahteva prazan konstruktor

    public Challenge(String creatorUid, String creatorUsername, String region,
                     int starsWager, int tokensWager) {
        this.creatorUid = creatorUid;
        this.creatorUsername = creatorUsername;
        this.region = region;
        this.starsWager = starsWager;
        this.tokensWager = tokensWager;
        this.status = STATUS_OPEN;
        this.participantUids = new ArrayList<>();
        this.participantUsernames = new ArrayList<>();
        this.participantScores = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();

        // Kreator automatski ulazi kao prvi učesnik
        this.participantUids.add(creatorUid);
        this.participantUsernames.add(creatorUsername);
        this.participantScores.add(0);
    }

    // --- getteri/setteri ---
    public String getId()                         { return id; }
    public void setId(String id)                  { this.id = id; }
    public String getCreatorUid()                 { return creatorUid; }
    public void setCreatorUid(String v)           { this.creatorUid = v; }
    public String getCreatorUsername()            { return creatorUsername; }
    public void setCreatorUsername(String v)      { this.creatorUsername = v; }
    public String getRegion()                     { return region; }
    public void setRegion(String v)               { this.region = v; }
    public int getStarsWager()                    { return starsWager; }
    public void setStarsWager(int v)              { this.starsWager = v; }
    public int getTokensWager()                   { return tokensWager; }
    public void setTokensWager(int v)             { this.tokensWager = v; }
    public String getStatus()                     { return status; }
    public void setStatus(String v)               { this.status = v; }
    public List<String> getParticipantUids()      { return participantUids; }
    public void setParticipantUids(List<String> v){ this.participantUids = v; }
    public List<String> getParticipantUsernames() { return participantUsernames; }
    public void setParticipantUsernames(List<String> v) { this.participantUsernames = v; }
    public List<Integer> getParticipantScores()   { return participantScores; }
    public void setParticipantScores(List<Integer> v)   { this.participantScores = v; }
    public long getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(long v)              { this.createdAt = v; }

    /** Koliko ukupno igrača je ušlo (kreator + ostali, max 4). */
    public int getParticipantCount() {
        return participantUids == null ? 0 : participantUids.size();
    }

    public boolean isFull() { return getParticipantCount() >= 4; }
    public boolean isOpen() { return STATUS_OPEN.equals(status); }
    public boolean isFinished() { return STATUS_FINISHED.equals(status); }
}