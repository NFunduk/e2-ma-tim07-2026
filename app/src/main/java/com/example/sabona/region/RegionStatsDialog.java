package com.example.sabona.region;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.sabona.R;

/**
 * Dijalog koji prikazuje statistiku jednog regiona (specifikacija,
 * tačka d.: broj trenutno aktivnih igrača i broj ukupno registrovanih
 * igrača). Prikazuje se na klik na region u {@link RegionsMapFragment}.
 *
 * Broj osvojenih 1./2./3. mesta (tačka d.i.) je namerno isključen ovde –
 * zavisi od mesečne rang liste po regionima koja se radi naknadno.
 */
public class RegionStatsDialog extends DialogFragment {

    private static final String ARG_REGION_NAME = "region_name";
    private static final String ARG_REGION_ICON = "region_icon";
    private static final String ARG_ACTIVE      = "active_players";
    private static final String ARG_TOTAL       = "total_players";

    public static RegionStatsDialog newInstance(RegionStats stats) {
        RegionStatsDialog dialog = new RegionStatsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_REGION_NAME, stats.region.displayName);
        args.putString(ARG_REGION_ICON, stats.region.icon);
        args.putInt(ARG_ACTIVE, stats.activePlayers);
        args.putInt(ARG_TOTAL, stats.totalPlayers);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String name   = args.getString(ARG_REGION_NAME, "");
        String icon   = args.getString(ARG_REGION_ICON, "📍");
        int active    = args.getInt(ARG_ACTIVE, 0);
        int total     = args.getInt(ARG_TOTAL, 0);

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_region_stats, null);

        TextView tvIcon   = view.findViewById(R.id.tvRegionIcon);
        TextView tvName   = view.findViewById(R.id.tvRegionName);
        TextView tvActive = view.findViewById(R.id.tvActivePlayers);
        TextView tvTotal  = view.findViewById(R.id.tvTotalPlayers);
        Button   btnClose = view.findViewById(R.id.btnCloseRegionStats);

        tvIcon.setText(icon);
        tvName.setText(name);
        tvActive.setText(String.valueOf(active));
        tvTotal.setText(String.valueOf(total));

        btnClose.setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create();
    }
}