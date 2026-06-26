package com.example.sabona.tournament;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.R;
import com.example.sabona.game.GameSessionManager;
import com.google.firebase.auth.FirebaseAuth;
import android.widget.ImageView;

public class TournamentFragment extends Fragment {

    private TournamentViewModel viewModel;
    private boolean navigated = false;

    private TextView tvStatus;
    private ProgressBar progressBar;
    private Button btnCancel;
    private TextView[] playerSlots = new TextView[4];
    private ImageView[] playerAvatars = new ImageView[4];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tournament, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameSessionManager.get().reset();

        tvStatus = view.findViewById(R.id.tvTournamentStatus);
        progressBar = view.findViewById(R.id.progressTournament);
        btnCancel = view.findViewById(R.id.btnCancelTournament);
        playerSlots[0] = view.findViewById(R.id.tvPlayerSlot1);
        playerSlots[1] = view.findViewById(R.id.tvPlayerSlot2);
        playerSlots[2] = view.findViewById(R.id.tvPlayerSlot3);
        playerSlots[3] = view.findViewById(R.id.tvPlayerSlot4);

        playerAvatars[0] = view.findViewById(R.id.ivPlayerAvatar1);
        playerAvatars[1] = view.findViewById(R.id.ivPlayerAvatar2);
        playerAvatars[2] = view.findViewById(R.id.ivPlayerAvatar3);
        playerAvatars[3] = view.findViewById(R.id.ivPlayerAvatar4);



        viewModel = new ViewModelProvider(this).get(TournamentViewModel.class);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Moraš biti prijavljen/a.", Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            if (state == TournamentViewModel.State.SEARCHING) {
                tvStatus.setText("Čekamo 4 igrača za turnir...");
                progressBar.setVisibility(View.VISIBLE);
                btnCancel.setEnabled(true);
            } else if (state == TournamentViewModel.State.MATCHED) {
                tvStatus.setText("Turnir počinje!");
                progressBar.setVisibility(View.GONE);
                btnCancel.setEnabled(false);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg == null) return;

            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).navigateUp();
        });

        viewModel.getPlayers().observe(getViewLifecycleOwner(), players -> {
            for (int i = 0; i < 4; i++) {
                final int index = i;

                if (players != null && index < players.size()) {
                    TournamentRepository.TournamentPlayer p = players.get(index);

                    playerSlots[index].setText(
                            (index + 1) + ". " + p.username + "  |  Liga " + p.league
                    );

                    int avatarResId = resolveAvatarRes(p.avatarRes);
                    if (avatarResId != 0) {
                        playerAvatars[index].setImageResource(avatarResId);
                    } else {
                        playerAvatars[index].setImageResource(R.drawable.outline_account_circle_24);
                    }

                    playerSlots[index].animate()
                            .alpha(0.4f)
                            .setDuration(150)
                            .withEndAction(() ->
                                    playerSlots[index].animate()
                                            .alpha(1f)
                                            .setDuration(150)
                                            .start()
                            )
                            .start();

                } else {
                    playerSlots[index].setText((index + 1) + ". Čeka se igrač...");
                    playerAvatars[index].setImageResource(R.drawable.outline_account_circle_24);
                }
            }
        });

        viewModel.getSessionId().observe(getViewLifecycleOwner(), sessionId -> {
            if (sessionId == null || navigated) return;
            navigated = true;

            Bundle args = new Bundle();
            args.putString("sessionId", sessionId);

            tvStatus.setText("Igrači su spojeni! Partija počinje...");
            view.postDelayed(() ->
                            NavHostFragment.findNavController(this)
                                    .navigate(R.id.action_tournament_to_koZnaZna, args),
                    1800
            );
        });

        btnCancel.setOnClickListener(v -> {
            viewModel.cancel();
            NavHostFragment.findNavController(this).navigateUp();
        });

        viewModel.start();
    }

    private int resolveAvatarRes(String name) {
        if (name == null) return 0;

        switch (name) {
            case "gallery":  return android.R.drawable.ic_menu_gallery;
            case "places":   return android.R.drawable.ic_menu_myplaces;
            case "compass":  return android.R.drawable.ic_menu_compass;
            case "camera":   return android.R.drawable.ic_menu_camera;
            case "manage":   return android.R.drawable.ic_menu_manage;
            case "search":   return android.R.drawable.ic_menu_search;
            case "share":    return android.R.drawable.ic_menu_share;
            case "send":     return android.R.drawable.ic_menu_send;
            case "add":      return android.R.drawable.ic_menu_add;
            case "info":     return android.R.drawable.ic_menu_info_details;
            case "edit":     return android.R.drawable.ic_menu_edit;
            case "view":     return android.R.drawable.ic_menu_view;
            default:         return 0;
        }
    }
}