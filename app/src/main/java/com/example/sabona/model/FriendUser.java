package com.example.sabona.model;

/** model koji se prikazuje u listi prijatelja. */
public class FriendUser {

    private String uid;
    private String username;
    private String avatarRes;
    private int    stars;
    private int    league;
    private int    monthlyRank;
    private boolean online;   // je li prijatelj trenutno ulogovan/aktivan u aplikaciji
    private boolean inGame;   // je li prijatelj trenutno u nekoj partiji

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

    public boolean isOnline()       { return online; }
    public void    setOnline(boolean v) { this.online = v; }

    public boolean isInGame()       { return inGame; }
    public void    setInGame(boolean v) { this.inGame = v; }

    /** Igrač je dostupan za poziv na partiju ako je ulogovan i trenutno nije u partiji. */
    public boolean isAvailableForGame() {
        return online && !inGame;
    }
}