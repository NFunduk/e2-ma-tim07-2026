package com.example.sabona.model;

/** model koji se prikazuje u listi prijatelja. */
public class FriendUser {

    private String uid;
    private String username;
    private String avatarRes;
    private int    stars;
    private int    league;
    // monthlyRank se može dodati kad rang lista postoji
    private int    monthlyRank;

    public FriendUser() {}

    public FriendUser(String uid, String username, String avatarRes, int stars, int league) {
        this.uid       = uid;
        this.username  = username;
        this.avatarRes = avatarRes;
        this.stars     = stars;
        this.league    = league;
    }

    public String getUid()              { return uid; }
    public void   setUid(String v)      { this.uid = v; }

    public String getUsername()             { return username != null ? username : ""; }
    public void   setUsername(String v)     { this.username = v; }

    public String getAvatarRes()            { return avatarRes; }
    public void   setAvatarRes(String v)    { this.avatarRes = v; }

    public int  getStars()          { return stars; }
    public void setStars(int v)     { this.stars = v; }

    public int  getLeague()         { return league; }
    public void setLeague(int v)    { this.league = v; }

    public int  getMonthlyRank()        { return monthlyRank; }
    public void setMonthlyRank(int v)   { this.monthlyRank = v; }
}