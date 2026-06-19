package com.example.sabona.model;

public class User {
    private String uid;
    private String email;
    private String username;
    private String region;
    private int tokens;
    private int stars;
    private int league;
    private int monthlyRank;   // trenutni mesečni rang igrača
    private boolean online;    // je li igrač trenutno ulogovan/aktivan u aplikaciji
    private boolean inGame;    // je li igrač trenutno u nekoj partiji


    public User() {}

    public User(String uid, String email, String username, String region) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.region = region;
        this.tokens = 5;   // 5 tokena pri registraciji
        this.stars = 0;
        this.league = 0;
        this.monthlyRank = 0;
        this.online = false;
        this.inGame = false;
    }

    public String getUid()      { return uid; }
    public String getEmail()    { return email; }
    public String getUsername() { return username; }
    public String getRegion()   { return region; }
    public int getTokens()      { return tokens; }
    public int getStars()       { return stars; }
    public int getLeague()      { return league; }
    public int getMonthlyRank() { return monthlyRank; }
    public boolean isOnline()   { return online; }
    public boolean isInGame()   { return inGame; }

    public void setUid(String uid)         { this.uid = uid; }
    public void setEmail(String email)     { this.email = email; }
    public void setUsername(String u)      { this.username = u; }
    public void setRegion(String region)   { this.region = region; }
    public void setTokens(int tokens)      { this.tokens = tokens; }
    public void setStars(int stars)        { this.stars = stars; }
    public void setLeague(int league)      { this.league = league; }
    public void setMonthlyRank(int monthlyRank) { this.monthlyRank = monthlyRank; }
    public void setOnline(boolean online)       { this.online = online; }
    public void setInGame(boolean inGame)       { this.inGame = inGame; }
}