package com.example.sabona.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.TextView;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.sabona.repository.NotificationFactory;
import com.example.sabona.repository.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.example.sabona.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvHomeUsername = view.findViewById(R.id.tvHomeUsername);

        String currentUid = FirebaseAuth.getInstance()
                .getCurrentUser()
                .getUid();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUid)
                .get()
                .addOnSuccessListener(document -> {
                    String username = document.getString("username");
                    if (username != null) tvHomeUsername.setText(username);
                });

        // normalan klik – pokreni igru
        view.findViewById(R.id.btnHomePlay).setOnClickListener(v -> {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            NotificationRepository repo = new NotificationRepository();
            repo.createNotification(uid, NotificationFactory.reward("Dobila si 3 tokena kao nagradu."));
            repo.createNotification(uid, NotificationFactory.rankingChanged(2));
            repo.createNotification(uid, NotificationFactory.leagueChanged("Srebrna liga"));
            repo.createNotification(uid, NotificationFactory.chatMessage("Marko"));
            repo.createNotification(uid, NotificationFactory.friendRequest("friend_request_1", "Ana"));
            repo.createNotification(uid, NotificationFactory.gameInvite("game_request_1", "Nikola"));
            BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottomNav);
            bottomNav.setSelectedItemId(R.id.play);
        });

        // Testovi za napredovanje kroz lige
        // dugim pritiskom na red lige u Profilu (com.example.sabona.league.LeagueTestMenu)
    }
}