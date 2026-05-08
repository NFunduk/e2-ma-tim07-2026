package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class GameOverFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game_over, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvWinner   = view.findViewById(R.id.tvGameOverWinner);
        Button btnPlayAgain = view.findViewById(R.id.btnPlayAgain);
        Button btnHome      = view.findViewById(R.id.btnGameOverHome);

        if (getArguments() != null) {
            tvWinner.setText(getArguments().getString("winner", ""));
        }

        btnPlayAgain.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_gameover_to_home));

        btnHome.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_gameover_to_home));
    }
}