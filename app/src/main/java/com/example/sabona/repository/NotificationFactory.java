package com.example.sabona.repository;

import com.example.sabona.model.AppNotification;

public class NotificationFactory {

    public static AppNotification chatMessage(String senderName) {
        return new AppNotification(
                "Nova poruka u četu",
                senderName + " ti je poslao/la poruku.",
                "Čet",
                "chat_message",
                null,
                false,
                false
        );
    }

    public static AppNotification rankingChanged(int position) {
        return new AppNotification(
                "Promena na rang listi",
                "Trenutno si na " + position + ". mestu.",
                "Rangiranje",
                "ranking",
                null,
                false,
                false
        );
    }

    public static AppNotification leagueChanged(String leagueName) {
        return new AppNotification(
                "Prelazak u novu ligu",
                "Prešla si u ligu: " + leagueName + ".",
                "Rangiranje",
                "league_up",
                null,
                false,
                false
        );
    }

    public static AppNotification reward(String rewardText) {
        return new AppNotification(
                "Osvojena nagrada",
                rewardText,
                "Nagrade",
                "reward",
                null,
                false,
                false
        );
    }

    public static AppNotification friendRequest(String requestId, String senderName) {
        return new AppNotification(
                "Zahtev za prijateljstvo",
                senderName + " želi da budete prijatelji.",
                "Ostalo",
                "friend_request",
                requestId,
                false,
                true
        );
    }

    public static AppNotification gameInvite(String gameRequestId, String senderName) {
        return new AppNotification(
                "Poziv za partiju",
                senderName + " te poziva da odigrate partiju.",
                "Ostalo",
                "friend_game_invite",
                gameRequestId,
                false,
                true
        );
    }

    public static AppNotification leaderboardReward(String cycleName, int position, int tokens) {
        return new AppNotification(
                "Nagrada za rang listu",
                "Osvojila si " + position + ". mesto na " + cycleName +
                        " rang listi i dobijaš " + tokens + " tokena.",
                "Nagrade",
                "leaderboard_reward",
                null,
                false,
                false
        );
    }

    public static AppNotification leaderboardPlacement(String cycleName, int position) {
        return new AppNotification(
                "Plasman na rang listi",
                "Trenutno si na " + position + ". mestu na " + cycleName + " rang listi.",
                "Rangiranje",
                "leaderboard_position",
                null,
                false,
                false
        );
    }
}