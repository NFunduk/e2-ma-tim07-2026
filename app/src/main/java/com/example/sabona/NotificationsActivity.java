package com.example.sabona;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private TextView filterAll, filterUnread, filterRead;
    private TextView channelChat, channelRanking, channelRewards, channelOther;

    private RecyclerView notificationsRecycler;

    private NotificationAdapter adapter;
    private final List<AppNotification> allNotifications = new ArrayList<>();

    private int currentFilter = 0; // 0 = sve, 1 = nepročitane, 2 = pročitane
    private String selectedChannel = "Svi"; // početno izabran kanal

    private TextView channelAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.notificationsRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        filterAll = findViewById(R.id.filterAll);
        filterUnread = findViewById(R.id.filterUnread);
        filterRead = findViewById(R.id.filterRead);

        channelChat = findViewById(R.id.channelChat);
        channelRanking = findViewById(R.id.channelRanking);
        channelRewards = findViewById(R.id.channelRewards);
        channelOther = findViewById(R.id.channelOther);
        channelAll = findViewById(R.id.channelAll);

        notificationsRecycler = findViewById(R.id.notificationsRecycler);

        prepareNotifications();

        adapter = new NotificationAdapter(this, new ArrayList<>());
        notificationsRecycler.setLayoutManager(new LinearLayoutManager(this));
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

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.home) {
                Intent intent = new Intent(NotificationsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            }
            return true;
        });

        selectChannel(channelAll, channelChat, channelRanking, channelRewards, channelOther);
        applyFilter();
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

    private void selectFilter(TextView selected, TextView firstOther, TextView secondOther) {
        selected.setBackgroundResource(R.drawable.filter_selected_bg);
        selected.setTextColor(getColor(R.color.white));

        firstOther.setBackgroundResource(R.drawable.filter_unselected_bg);
        firstOther.setTextColor(getColor(R.color.dark_blue));

        secondOther.setBackgroundResource(R.drawable.filter_unselected_bg);
        secondOther.setTextColor(getColor(R.color.dark_blue));
    }

    private void selectChannel(TextView selected, TextView... others) {
        selected.setBackgroundResource(R.drawable.filter_selected_bg);
        selected.setTextColor(getColor(R.color.white));

        for (TextView other : others) {
            other.setBackgroundResource(R.drawable.channel_chip_bg);
            other.setTextColor(getColor(R.color.dark_blue));
        }
    }
}