package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class NotificationFragment extends Fragment {

    private TextView filterAll, filterUnread, filterRead;
    private TextView channelChat, channelRanking, channelRewards, channelOther, channelAll;

    private RecyclerView notificationsRecycler;

    private NotificationAdapter adapter;
    private final List<AppNotification> allNotifications = new ArrayList<>();

    private int currentFilter = 0;
    private String selectedChannel = "Svi";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        filterAll = view.findViewById(R.id.filterAll);
        filterUnread = view.findViewById(R.id.filterUnread);
        filterRead = view.findViewById(R.id.filterRead);

        channelChat = view.findViewById(R.id.channelChat);
        channelRanking = view.findViewById(R.id.channelRanking);
        channelRewards = view.findViewById(R.id.channelRewards);
        channelOther = view.findViewById(R.id.channelOther);
        channelAll = view.findViewById(R.id.channelAll);

        notificationsRecycler = view.findViewById(R.id.notificationsRecycler);

        prepareNotifications();

        adapter = new NotificationAdapter(requireContext(), new ArrayList<>());

        notificationsRecycler.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        notificationsRecycler.setAdapter(adapter);

        filterAll.setOnClickListener(v -> {
            currentFilter = 0;
            selectFilter(filterAll, filterUnread, filterRead);
            applyFilter();
        });

        filterUnread.setOnClickListener(v -> {
            currentFilter = 1;
            selectFilter(filterUnread, filterAll, filterRead);
            applyFilter();
        });

        filterRead.setOnClickListener(v -> {
            currentFilter = 2;
            selectFilter(filterRead, filterAll, filterUnread);
            applyFilter();
        });

        channelChat.setOnClickListener(v -> {
            selectedChannel = "Čet";
            selectChannel(channelChat, channelAll, channelRanking, channelRewards, channelOther);
            applyFilter();
        });

        channelRanking.setOnClickListener(v -> {
            selectedChannel = "Rangiranje";
            selectChannel(channelRanking, channelAll, channelChat, channelRewards, channelOther);
            applyFilter();
        });

        channelRewards.setOnClickListener(v -> {
            selectedChannel = "Nagrade";
            selectChannel(channelRewards, channelAll, channelChat, channelRanking, channelOther);
            applyFilter();
        });

        channelOther.setOnClickListener(v -> {
            selectedChannel = "Ostalo";
            selectChannel(channelOther, channelAll, channelChat, channelRanking, channelRewards);
            applyFilter();
        });

        channelAll.setOnClickListener(v -> {
            selectedChannel = "Svi";
            selectChannel(channelAll, channelChat, channelRanking, channelRewards, channelOther);
            applyFilter();
        });

        selectChannel(channelAll, channelChat, channelRanking, channelRewards, channelOther);

        applyFilter();

        return view;
    }

    private void prepareNotifications() {

        allNotifications.add(new AppNotification(
                "Nova poruka",
                "Marko ti je poslao poruku u četu.",
                "Čet",
                "pre 1 min",
                false,
                false,
                "chat"
        ));

        allNotifications.add(new AppNotification(
                "Promena na rang listi",
                "Pomeri/la si se na #12 mesto na rang listi.",
                "Rangiranje",
                "pre 2 min",
                false,
                false,
                "ranking"
        ));

        allNotifications.add(new AppNotification(
                "Nova nagrada",
                "Osvojio/la si bonus poene za današnju aktivnost.",
                "Nagrade",
                "pre 10 min",
                false,
                false,
                "reward"
        ));

        allNotifications.add(new AppNotification(
                "Zahtev za prijatelja",
                "Mila želi da te doda za prijatelja.",
                "Ostalo",
                "pre 1 h",
                true,
                true,
                "friend"
        ));

        allNotifications.add(new AppNotification(
                "Prelazak u ligu",
                "Prešao/la si u višu ligu. Pogledaj novu poziciju.",
                "Rangiranje",
                "juče",
                true,
                false,
                "league"
        ));
    }

    private void applyFilter() {

        List<AppNotification> filtered = new ArrayList<>();

        for (AppNotification notification : allNotifications) {

            boolean matchesReadFilter =
                    currentFilter == 0 ||
                            (currentFilter == 1 && !notification.isRead()) ||
                            (currentFilter == 2 && notification.isRead());

            boolean matchesChannel =
                    selectedChannel.equals("Svi") ||
                            notification.getChannel().equals(selectedChannel);

            if (matchesReadFilter && matchesChannel) {
                filtered.add(notification);
            }
        }

        adapter.setNotifications(filtered);
    }

    private void selectFilter(TextView selected,
                              TextView firstOther,
                              TextView secondOther) {

        selected.setBackgroundResource(R.drawable.filter_selected_bg);
        selected.setTextColor(requireContext().getColor(R.color.white));

        firstOther.setBackgroundResource(R.drawable.filter_unselected_bg);
        firstOther.setTextColor(requireContext().getColor(R.color.dark_blue));

        secondOther.setBackgroundResource(R.drawable.filter_unselected_bg);
        secondOther.setTextColor(requireContext().getColor(R.color.dark_blue));
    }

    private void selectChannel(TextView selected, TextView... others) {

        selected.setBackgroundResource(R.drawable.filter_selected_bg);
        selected.setTextColor(requireContext().getColor(R.color.white));

        for (TextView other : others) {
            other.setBackgroundResource(R.drawable.channel_chip_bg);
            other.setTextColor(requireContext().getColor(R.color.dark_blue));
        }
    }
}