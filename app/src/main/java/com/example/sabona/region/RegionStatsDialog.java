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
 * tačka d.):
 *  - broj trenutno aktivnih igrača (d.ii)
 *  - broj ukupno registrovanih igrača (d.iii)
 *  - broj osvojenih 1./2./3. mesta kroz sve mesečne cikluse (d.i)
 *  - ukupno zvezda regiona u tekućem mesečnom ciklusu (5.b, kontekst)
 *
 * Prikazuje se na klik na region u {@link RegionsMapFragment}.
 */
public class RegionStatsDialog extends DialogFragment {

    private static final String ARG_REGION_NAME = "region_name";
    private static final String ARG_REGION_ICON = "region_icon";
    private static final String ARG_ACTIVE      = "active_players";
    private static final String ARG_TOTAL       = "total_players";
    private static final String ARG_MONTHLY_STARS = "monthly_stars";
    private static final String ARG_FIRST  = "first_place";
    private static final String ARG_SECOND = "second_place";
    private static final String ARG_THIRD  = "third_place";

    public static RegionStatsDialog newInstance(RegionStats stats) {
        RegionStatsDialog dialog = new RegionStatsDialog();
        Bundle args = new Bundle();
        args.putString(ARG_REGION_NAME, stats.region.displayName);
        args.putString(ARG_REGION_ICON, stats.region.icon);
        args.putInt(ARG_ACTIVE, stats.activePlayers);
        args.putInt(ARG_TOTAL, stats.totalPlayers);
        args.putLong(ARG_MONTHLY_STARS, stats.monthlyStarsCurrentCycle);
        args.putInt(ARG_FIRST, stats.firstPlaceFinishes);
        args.putInt(ARG_SECOND, stats.secondPlaceFinishes);
        args.putInt(ARG_THIRD, stats.thirdPlaceFinishes);
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
        long monthlyStars = args.getLong(ARG_MONTHLY_STARS, 0);
        int first  = args.getInt(ARG_FIRST, 0);
        int second = args.getInt(ARG_SECOND, 0);
        int third  = args.getInt(ARG_THIRD, 0);

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_region_stats, null);

        TextView tvIcon   = view.findViewById(R.id.tvRegionIcon);
        TextView tvName   = view.findViewById(R.id.tvRegionName);
        TextView tvActive = view.findViewById(R.id.tvActivePlayers);
        TextView tvTotal  = view.findViewById(R.id.tvTotalPlayers);
        TextView tvMonthlyStars = view.findViewById(R.id.tvMonthlyStars);
        TextView tvFirst  = view.findViewById(R.id.tvFirstPlaceCount);
        TextView tvSecond = view.findViewById(R.id.tvSecondPlaceCount);
        TextView tvThird  = view.findViewById(R.id.tvThirdPlaceCount);
        Button   btnClose = view.findViewById(R.id.btnCloseRegionStats);

        tvIcon.setText(icon);
        tvName.setText(name);
        tvActive.setText(String.valueOf(active));
        tvTotal.setText(String.valueOf(total));
        tvMonthlyStars.setText(String.valueOf(monthlyStars));
        tvFirst.setText(String.valueOf(first));
        tvSecond.setText(String.valueOf(second));
        tvThird.setText(String.valueOf(third));

        btnClose.setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(true)
                .create();
    }
}