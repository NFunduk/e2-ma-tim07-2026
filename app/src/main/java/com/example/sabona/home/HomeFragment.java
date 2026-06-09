package com.example.sabona.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.sabona.model.AppNotification;
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

    // HomeFragment.java

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnHomePlay).setOnClickListener(v -> {

            String uid = FirebaseAuth.getInstance()
                    .getCurrentUser()
                    .getUid();

            NotificationRepository repo = new NotificationRepository();

            Toast.makeText(requireContext(), "Kliknuto Play", Toast.LENGTH_SHORT).show();

            repo.createNotification(
                    uid,
                    new AppNotification(
                            "Test notifikacija",
                            "Ovo je test sistemske notifikacije",
                            "Nagrade",
                            "reward",
                            null,
                            false,
                            false
                    )
            );

            BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottomNav);
            bottomNav.setSelectedItemId(R.id.play);
        });
    }
}