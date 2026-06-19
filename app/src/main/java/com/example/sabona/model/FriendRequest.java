package com.example.sabona.model;

import com.google.firebase.Timestamp;

public class FriendRequest {

    private String id;
    private String fromUid;
    private String fromUsername;
    private String toUid;
    private String status; // "pending", "accepted", "rejected"
    private Timestamp createdAt;

    public FriendRequest() {}

    public FriendRequest(String fromUid, String fromUsername, String toUid) {
        this.fromUid     = fromUid;
        this.fromUsername = fromUsername;
        this.toUid       = toUid;
        this.status      = "pending";
        this.createdAt   = Timestamp.now();
    }

    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getFromUid()              { return fromUid; }
    public void   setFromUid(String v)      { this.fromUid = v; }

    public String getFromUsername()             { return fromUsername; }
    public void   setFromUsername(String v)     { this.fromUsername = v; }

    public String getToUid()            { return toUid; }
    public void   setToUid(String v)    { this.toUid = v; }

    public String getStatus()           { return status; }
    public void   setStatus(String v)   { this.status = v; }

    public Timestamp getCreatedAt()             { return createdAt; }
    public void      setCreatedAt(Timestamp v)  { this.createdAt = v; }
}