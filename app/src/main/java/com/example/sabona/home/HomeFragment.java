package com.example.sabona.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.widget.TextView;

import com.example.sabona.auth.AuthRepository;
import com.google.firebase.firestore.FirebaseFirestore;

import com.google.firebase.auth.FirebaseAuth;
import com.example.sabona.R;
import android.widget.Toast;
import com.example.sabona.daily.DailyMissionRepository;
import com.google.firebase.firestore.ListenerRegistration;

public class HomeFragment extends Fragment {

    private TextView tvMission1Status;
    private TextView tvMission2Status;
    private TextView tvMission3Status;
    private TextView tvMission4Status;
    private TextView tvMissionBonus;

    private ListenerRegistration userListener;
    private final DailyMissionRepository dailyMissionRepository = new DailyMissionRepository();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Zaštita: HomeFragment se može kratko ponovo iscrtati dok se backstack
        // čisti tokom logout-a, prije nego navigacija stigne do login ekrana.
        // Bez ove provjere getCurrentUser() vraća null i aplikacija puca (NPE).
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        TextView tvHomeUsername = view.findViewById(R.id.tvHomeUsername);

        tvMission1Status = view.findViewById(R.id.tvMission1Status);
        tvMission2Status = view.findViewById(R.id.tvMission2Status);
        tvMission3Status = view.findViewById(R.id.tvMission3Status);
        tvMission4Status = view.findViewById(R.id.tvMission4Status);
        tvMissionBonus = view.findViewById(R.id.tvMissionBonus);

        String currentUid = FirebaseAuth.getInstance()
                .getCurrentUser()
                .getUid();
        dailyMissionRepository.ensureTodayMissions(currentUid);

        new AuthRepository().grantDailyTokensIfNeeded(new AuthRepository.Callback() {
            @Override public void onSuccess() { }
            @Override public void onError(String message) { }
        });

        userListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUid)
                .addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) return;

                    String username = document.getString("username");
                    if (username != null) tvHomeUsername.setText(username);

                    boolean m1 = Boolean.TRUE.equals(document.getBoolean("missionWinMatch"));
                    boolean m2 = Boolean.TRUE.equals(document.getBoolean("missionSendChat"));
                    boolean m3 = Boolean.TRUE.equals(document.getBoolean("missionFriendlyMatch"));
                    boolean m4 = Boolean.TRUE.equals(document.getBoolean("missionTournamentWin"));

                    tvMission1Status.setText(m1 ? "1/1" : "0/1");
                    tvMission2Status.setText(m2 ? "1/1" : "0/1");
                    tvMission3Status.setText(m3 ? "1/1" : "0/1");
                    tvMission4Status.setText(m4 ? "1/1" : "0/1");

                    int completed = 0;
                    if (m1) completed++;
                    if (m2) completed++;
                    if (m3) completed++;
                    if (m4) completed++;

                    tvMissionBonus.setText(completed + "/4");
                });

        // klik – pokreni igru (Navigation Component umesto BottomNav selekcije)
        view.findViewById(R.id.btnHomePlay).setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_home_to_matchmaking));

        tvMission1Status.setOnLongClickListener(v -> {
            dailyMissionRepository.completeWinMatch(currentUid, null);
            Toast.makeText(requireContext(), "Test: završena misija Pobedi partiju", Toast.LENGTH_SHORT).show();
            return true;
        });

        tvMission2Status.setOnLongClickListener(v -> {
            dailyMissionRepository.completeSendChat(currentUid, null);
            Toast.makeText(requireContext(), "Test: završena misija Čet", Toast.LENGTH_SHORT).show();
            return true;
        });

        tvMission3Status.setOnLongClickListener(v -> {
            dailyMissionRepository.completeFriendlyMatch(currentUid, null);
            Toast.makeText(requireContext(), "Test: završena misija Prijateljska partija", Toast.LENGTH_SHORT).show();
            return true;
        });

        tvMission4Status.setOnLongClickListener(v -> {
            dailyMissionRepository.completeTournamentWin(currentUid, null);
            Toast.makeText(requireContext(), "Test: završena misija Turnir", Toast.LENGTH_SHORT).show();
            return true;
        });

        // Testovi za napredovanje kroz lige su premješteni: sada se pokreću
        // dugim pritiskom na red lige u Profilu (com.example.sabona.league.LeagueTestMenu)
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }
}