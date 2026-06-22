package com.example.sabona;

import android.app.Dialog;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public class RewardDialogFragment extends DialogFragment {

    private MediaPlayer mediaPlayer;

    public static RewardDialogFragment newInstance(String message) {
        RewardDialogFragment fragment = new RewardDialogFragment();

        Bundle args = new Bundle();
        args.putString("message", message);
        fragment.setArguments(args);

        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_reward, null);

        TextView tvMessage = view.findViewById(R.id.tvRewardMessage);
        ImageView imgReward = view.findViewById(R.id.imgReward);

        String message = getArguments() != null
                ? getArguments().getString("message", "Osvojila si nagradu!")
                : "Osvojila si nagradu!";

        tvMessage.setText(message);

        ScaleAnimation animation = new ScaleAnimation(
                0.7f, 1.15f,
                0.7f, 1.15f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );

        animation.setDuration(600);
        animation.setRepeatCount(5);
        animation.setRepeatMode(Animation.REVERSE);
        imgReward.startAnimation(animation);

        try {
            mediaPlayer = MediaPlayer.create(requireContext(), R.raw.reward_sound);
            if (mediaPlayer != null) {
                mediaPlayer.start();
            }
        } catch (Exception ignored) {
        }

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton("Super!", (dialog, which) -> dismiss())
                .create();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}