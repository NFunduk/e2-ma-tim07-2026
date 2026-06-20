package com.example.sabona.league;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.sabona.R;

public class LeagueProgressFragment extends Fragment {

    private LeagueViewModel viewModel;

    private ImageView  ivLeagueIcon;
    private TextView   tvLeagueName;
    private TextView   tvLeagueIndex;
    private TextView   tvStarsCount;
    private ProgressBar progressLeague;
    private TextView   tvStarsToNext;
    private TextView   tvDailyBonus;
    private TextView   tvDailyBonusDetail;
    private LinearLayout layoutLeagueList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_league_progress, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivLeagueIcon       = view.findViewById(R.id.ivLeagueIcon);
        tvLeagueName       = view.findViewById(R.id.tvLeagueName);
        tvLeagueIndex      = view.findViewById(R.id.tvLeagueIndex);
        tvStarsCount       = view.findViewById(R.id.tvStarsCount);
        progressLeague     = view.findViewById(R.id.progressLeague);
        tvStarsToNext      = view.findViewById(R.id.tvStarsToNext);
        tvDailyBonus       = view.findViewById(R.id.tvDailyBonus);
        tvDailyBonusDetail = view.findViewById(R.id.tvDailyBonusDetail);
        layoutLeagueList   = view.findViewById(R.id.layoutLeagueList);

        buildLeagueList();

        viewModel = new ViewModelProvider(this).get(LeagueViewModel.class);

        viewModel.getStars().observe(getViewLifecycleOwner(), this::updateStarsUI);
        viewModel.getLeague().observe(getViewLifecycleOwner(), this::updateLeagueUI);
        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getLeagueChangeEvent().observe(getViewLifecycleOwner(), event -> {
            if (event != null) {
                LeagueChangeDialog.newInstance(
                        event.oldLeague.displayName,
                        event.newLeague.displayName,
                        event.promoted
                ).show(getChildFragmentManager(), "league_change");
            }
        });

        viewModel.loadData();
    }

    // ------------------------------------------------------------------ //

    private void updateStarsUI(int stars) {
        tvStarsCount.setText(String.valueOf(stars));

        int progress = (int) (LeagueManager.leagueProgress(stars) * 100);
        progressLeague.setProgress(progress);

        int toNext = LeagueManager.starsToNextLeague(stars);
        tvStarsToNext.setText(toNext == 0
                ? "Maksimalna liga! 🏆"
                : "Još " + toNext + " ⭐ do sljedeće lige");

        // Označi trenutnu ligu u listi
        highlightCurrentLeague(League.forStars(stars));
    }

    private void updateLeagueUI(League league) {
        tvLeagueName.setText(league.displayName);
        tvLeagueIndex.setText("Liga " + league.index);

        int dailyTotal = LeagueManager.dailyTokens(league);
        tvDailyBonus.setText(dailyTotal + " tokena");
        tvDailyBonusDetail.setText("5 baznih\n+" + league.dailyBonusTokens + " bonus");

        int iconResId = getResources().getIdentifier(
                league.iconResName, "drawable", requireContext().getPackageName());
        if (iconResId != 0) ivLeagueIcon.setImageResource(iconResId);

        highlightCurrentLeague(league);
    }

    // ------------------------------------------------------------------ //
    //  Dinamička lista liga                                                //
    // ------------------------------------------------------------------ //

    private void buildLeagueList() {
        layoutLeagueList.removeAllViews();
        League[] leagues = League.values();
        for (int i = 0; i < leagues.length; i++) {
            League l = leagues[i];
            View row = buildLeagueRow(l);
            row.setTag(l.index);
            layoutLeagueList.addView(row);

            // Separator (ne poslje zadnjeg)
            if (i < leagues.length - 1) {
                View sep = new View(requireContext());
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                p.setMarginStart(56);
                p.setMarginEnd(16);
                sep.setLayoutParams(p);
                sep.setBackgroundColor(0xFFEEEEEE);
                layoutLeagueList.addView(sep);
            }
        }
    }

    private View buildLeagueRow(League league) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = dp(12);
        row.setPadding(pad, dp(8), pad, dp(8));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Ikona
        ImageView icon = new ImageView(requireContext());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconParams.setMarginEnd(dp(12));
        icon.setLayoutParams(iconParams);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconRes = getResources().getIdentifier(
                league.iconResName, "drawable", requireContext().getPackageName());
        if (iconRes != 0) icon.setImageResource(iconRes);
        row.addView(icon);

        // Naziv + prag
        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textParams);

        TextView tvName = new TextView(requireContext());
        tvName.setTextSize(14);
        tvName.setTextColor(0xFF0B1957); // dark_blue
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setText(league.displayName);
        textCol.addView(tvName);

        TextView tvThreshold = new TextView(requireContext());
        tvThreshold.setTextSize(12);
        tvThreshold.setTextColor(0xFF777777);
        tvThreshold.setText(league.starsRequired == 0
                ? "Početna liga" : "Od " + league.starsRequired + " ⭐");
        textCol.addView(tvThreshold);
        row.addView(textCol);

        // Bonus tokeni
        TextView tvBonus = new TextView(requireContext());
        tvBonus.setTextSize(13);
        tvBonus.setTextColor(0xFF426BC2); // blue
        tvBonus.setTypeface(null, android.graphics.Typeface.BOLD);
        int total = LeagueManager.dailyTokens(league);
        tvBonus.setText("+" + total + " 🎁");
        tvBonus.setGravity(android.view.Gravity.END);
        row.addView(tvBonus);

        return row;
    }

    private void highlightCurrentLeague(League current) {
        if (layoutLeagueList == null) return;
        for (int i = 0; i < layoutLeagueList.getChildCount(); i++) {
            View child = layoutLeagueList.getChildAt(i);
            if (child.getTag() instanceof Integer) {
                int idx = (int) child.getTag();
                if (idx == current.index) {
                    child.setBackgroundColor(0x1A426BC2); // plavi highlight
                } else {
                    child.setBackgroundColor(0x00000000); // transparent
                }
            }
        }
    }

    private int dp(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
