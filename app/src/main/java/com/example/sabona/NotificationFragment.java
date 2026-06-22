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

import androidx.navigation.Navigation;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.Map;

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
            if (notification.getDataId() == null) {
                repository.markAsHandled(notification.getId());
                return;
            }

            String myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

            String gameRequestId = notification.getDataId();
            com.google.firebase.firestore.DocumentReference gameReqRef =
                    db.collection("gameRequests").document(gameRequestId);

            // Transakcija: status se menja samo ako je zahtev još "pending" — sprečava
            // da prihvatanje/odbijanje "pobedi" auto-odbijanje nakon 10s. Vraća fromUid
            // ako je promena uspela, null ako je zahtev već istekao/otkazan.
            db.runTransaction(transaction -> {
                com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(gameReqRef);
                String currentStatus = snap.getString("status");
                if (!"pending".equals(currentStatus)) {
                    return null;
                }
                transaction.update(gameReqRef, "status", accepted ? "accepted" : "rejected");
                return snap.getString("fromUid");
            }).addOnSuccessListener(fromUidResult -> {
                repository.markAsHandled(notification.getId());

                if (fromUidResult == null) {
                    Toast.makeText(requireContext(), "Poziv je već istekao ili otkazan.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!accepted) {
                    Toast.makeText(requireContext(), "Odbila si poziv za partiju", Toast.LENGTH_SHORT).show();
                    return;
                }

                startFriendlyMatchAsGuest((String) fromUidResult, myUid, gameRequestId);

            }).addOnFailureListener(e ->
                    Toast.makeText(requireContext(), "Greška pri obradi poziva", Toast.LENGTH_SHORT).show());

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

        if ("leaderboard_reward".equals(notification.getType())) {
            repository.markAsRead(notification.getId());

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showRewardDialog(notification.getMessage());
            }

            return;
        }

        repository.markAsRead(notification.getId());


    }

    private void startFriendlyMatchAsGuest(String fromUid, String myUid, String gameRequestId) {
        if (fromUid == null || myUid == null || !isAdded()) return;

        String sessionId = "F" + (System.currentTimeMillis() % 1000000) + (int) (Math.random() * 90 + 10);

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("status", "active");
        sessionData.put("isFriendlyMatch", true);
        sessionData.put("player1Uid", fromUid);
        sessionData.put("player2Uid", myUid);
        sessionData.put("totalScoreP1", 0);
        sessionData.put("totalScoreP2", 0);
        sessionData.put("leftByUid", null);
        sessionData.put("createdAt", FieldValue.serverTimestamp());

        db.collection("gameSessions").document(sessionId).set(sessionData)
                .addOnSuccessListener(v -> {
                    db.collection("gameRequests").document(gameRequestId)
                            .update("sessionId", sessionId);

                    if (!isAdded()) return;

                    Bundle args = new Bundle();
                    args.putString("sessionId", sessionId);
                    args.putBoolean("isHost", false);
                    args.putString("hostUid", fromUid);

                    try {
                        Navigation.findNavController(requireView())
                                .navigate(R.id.action_notifications_to_koZnaZna, args);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Greška pri pokretanju partije", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Greška pri kreiranju partije", Toast.LENGTH_SHORT).show());
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