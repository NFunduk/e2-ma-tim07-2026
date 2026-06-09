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
}