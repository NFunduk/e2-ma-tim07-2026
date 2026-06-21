package com.example.sabona.matchmaking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MatchmakingFragment extends Fragment {

    private TextView tvStatus;
    private ProgressBar progressBar;
    private Button btnCancel;
    private MatchmakingViewModel viewModel;
    private boolean navigated = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_matchmaking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvStatus    = view.findViewById(R.id.tvMatchmakingStatus);
        progressBar = view.findViewById(R.id.progressBarMatchmaking);
        btnCancel   = view.findViewById(R.id.btnCancelMatchmaking);

        viewModel = new ViewModelProvider(this).get(MatchmakingViewModel.class);

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid == null) {
            Toast.makeText(requireContext(), "Moraš biti prijavljen/a da igraš partiju.", Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(requireContext(), "Moraš biti prijavljen/a.", Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        if (firebaseUser.isAnonymous()) {
            Toast.makeText(requireContext(),
                    "Gosti mogu igrati samo prijateljske partije. Registruj se za matchmaking!",
                    Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            switch (state) {
                case SEARCHING:
                    tvStatus.setText("Tražim protivnika...");
                    progressBar.setVisibility(View.VISIBLE);
                    btnCancel.setEnabled(true);
                    break;
                case ERROR:
                    progressBar.setVisibility(View.GONE);
                    break;
                case MATCHED:
                    tvStatus.setText("Protivnik pronađen! Počinjemo...");
                    btnCancel.setEnabled(false);
                    break;
            }
        });

        viewModel.getErrorMsg().observe(getViewLifecycleOwner(), msg -> {
            if (msg == null) return;
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).navigateUp();
        });

        viewModel.getMatchResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null || navigated) return;
            navigated = true;

            Bundle args = new Bundle();
            args.putString("sessionId", result[0]);
            args.putBoolean("isHost", "1".equals(result[1]));

            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_matchmaking_to_koZnaZna, args);
        });

        btnCancel.setOnClickListener(v -> {
            viewModel.cancel();
            NavHostFragment.findNavController(this).navigateUp();
        });

        viewModel.startSearching(uid);
    }
}