package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.friends.FriendsRepository;
import com.example.sabona.model.AppNotification;
import com.example.sabona.repository.NotificationRepository;
import com.example.sabona.utils.NotificationHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

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

    private FirebaseFirestore db;
    private ListenerRegistration listenerRegistration;
    private NotificationRepository repository;
    private FriendsRepository friendsRepository;
    private Button btnMarkAllRead;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        filterAll = view.findViewById(R.id.filterAll);
        filterUnread = view.findViewById(R.id.filterUnread);
        filterRead = view.findViewById(R.id.filterRead);
        btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead);

        channelChat = view.findViewById(R.id.channelChat);
        channelRanking = view.findViewById(R.id.channelRanking);
        channelRewards = view.findViewById(R.id.channelRewards);
        channelOther = view.findViewById(R.id.channelOther);
        channelAll = view.findViewById(R.id.channelAll);

        notificationsRecycler = view.findViewById(R.id.notificationsRecycler);

        db = FirebaseFirestore.getInstance();
        repository = new NotificationRepository();
        friendsRepository = new FriendsRepository();

        adapter = new NotificationAdapter(
                requireContext(),
                new ArrayList<>(),
                new NotificationAdapter.NotificationActionListener() {
                    @Override
                    public void onMarkAsRead(AppNotification notification) {
                        repository.markAsRead(notification.getId());
                    }

                    @Override
                    public void onAccept(AppNotification notification) {
                        handleNotificationAction(notification, true);
                    }

                    @Override
                    public void onReject(AppNotification notification) {
                        handleNotificationAction(notification, false);
                    }
                }
        );

        notificationsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        notificationsRecycler.setAdapter(adapter);

        setupFilters();

        btnMarkAllRead.setOnClickListener(v -> {
            repository.markAllAsRead();
            Toast.makeText(requireContext(), "Sve notifikacije su označene kao pročitane", Toast.LENGTH_SHORT).show();
        });

        selectFilter(filterAll, filterUnread, filterRead);
        selectChannel(channelAll, channelChat, channelRanking, channelRewards, channelOther);

        listenForNotifications();

        return view;
    }

    private void setupFilters() {
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

        channelAll.setOnClickListener(v -> {
            selectedChannel = "Svi";
            selectChannel(channelAll, channelChat, channelRanking, channelRewards, channelOther);
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
    }

    private void listenForNotifications() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Korisnik nije ulogovan", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        listenerRegistration = db.collection("users")
                .document(uid)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(requireContext(), "Greška pri učitavanju notifikacija", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots == null) return;

                    allNotifications.clear();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        AppNotification notification = doc.toObject(AppNotification.class);

                        if (notification != null) {
                            notification.setId(doc.getId());
                            allNotifications.add(notification);
                        }
                    }

                    applyFilter();
                });
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

    private void handleNotificationAction(AppNotification notification, boolean accepted) {
        if ("friend_game_invite".equals(notification.getType())) {
            if (notification.getDataId() != null) {
                db.collection("gameRequests")
                        .document(notification.getDataId())
                        .update("status", accepted ? "accepted" : "rejected");
            }

            repository.markAsHandled(notification.getId());

            Toast.makeText(
                    requireContext(),
                    accepted ? "Prihvatila si poziv za partiju" : "Odbila si poziv za partiju",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if ("friend_request".equals(notification.getType())) {
            String requestId = notification.getDataId();

            if (requestId == null) {
                repository.markAsHandled(notification.getId());
                return;
            }

            if (accepted) {
                friendsRepository.acceptFriendRequest(requestId, new FriendsRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        repository.markAsHandled(notification.getId());
                        Toast.makeText(requireContext(),
                                "Prihvatila si zahtev za prijatelja",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                friendsRepository.rejectFriendRequest(requestId, new FriendsRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        repository.markAsHandled(notification.getId());
                        Toast.makeText(requireContext(),
                                "Odbila si zahtev za prijatelja",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            return;
        }

        repository.markAsRead(notification.getId());
    }

    private void selectFilter(TextView selected, TextView firstOther, TextView secondOther) {
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}