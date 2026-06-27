package com.example.sabona.league;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.sabona.R;

public class LeagueChangeDialog extends DialogFragment {

    private static final String ARG_OLD = "old_league";
    private static final String ARG_NEW = "new_league";
    private static final String ARG_UP = "promoted";

    public static LeagueChangeDialog newInstance(String oldLeagueName,
                                                 String newLeagueName,
                                                 boolean promoted) {
        LeagueChangeDialog dialog = new LeagueChangeDialog();
        Bundle args = new Bundle();
        args.putString(ARG_OLD, oldLeagueName);
        args.putString(ARG_NEW, newLeagueName);
        args.putBoolean(ARG_UP, promoted);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String oldName = getArguments() != null ? getArguments().getString(ARG_OLD, "") : "";
        String newName = getArguments() != null ? getArguments().getString(ARG_NEW, "") : "";
        boolean promoted = getArguments() != null && getArguments().getBoolean(ARG_UP, true);

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_league_change, null);

        TextView tvTitle = view.findViewById(R.id.tvLeagueChangeTitle);
        TextView tvMessage = view.findViewById(R.id.tvLeagueChangeMessage);
        ImageView ivArrow = view.findViewById(R.id.ivLeagueChangeArrow);
        Button btnOk = view.findViewById(R.id.btnLeagueChangeOk);

        if (promoted) {
            tvTitle.setText("Napredak u ligi");
            tvMessage.setText("Presao/la si iz \"" + oldName + "\"\nu \"" + newName + "\".");
            ivArrow.setImageResource(android.R.drawable.arrow_up_float);
        } else {
            tvTitle.setText("Promena lige");
            tvMessage.setText("Spustio/la si se iz \"" + oldName + "\"\nna \"" + newName + "\".");
            ivArrow.setImageResource(android.R.drawable.arrow_down_float);
        }

        btnOk.setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create();
    }
}
