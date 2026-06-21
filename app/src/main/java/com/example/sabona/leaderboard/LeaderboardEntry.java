package com.example.sabona.leaderboard;

public class LeaderboardEntry {

    private String userId;
    private String username;
    private int league;
    private int stars;
    private int gamesPlayed;

    public LeaderboardEntry() {
    }

    public LeaderboardEntry(String userId, String username, int league, int stars, int gamesPlayed) {
        this.userId = userId;
        this.username = username;
        this.league = league;
        this.stars = stars;
        this.gamesPlayed = gamesPlayed;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public int getLeague() {
        return league;
    }

    public int getStars() {
        return stars;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }
}