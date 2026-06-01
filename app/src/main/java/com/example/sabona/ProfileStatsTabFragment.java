package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileStatsTabFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_tab_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadMockStats(view);
    }

    private void loadMockStats(View view) {
        ((TextView) view.findViewById(R.id.tvTotalGames)).setText("87");
        ((TextView) view.findViewById(R.id.tvWinPercent)).setText("62%");
        ((TextView) view.findViewById(R.id.tvLossPercent)).setText("38%");

        ((TextView) view.findViewById(R.id.tvStatsKoZnaZna)).setText("15 - 35 bod.");
        ((TextView) view.findViewById(R.id.tvStatsKzzRatio)).setText("32 / 8");

        ((TextView) view.findViewById(R.id.tvStatsSpojnice)).setText("8 - 16 bod.");
        ((TextView) view.findViewById(R.id.tvStatsSpojnicePercent)).setText("78%");

        ((TextView) view.findViewById(R.id.tvStatsAsocijacije)).setText("10 - 40 bod.");
        ((TextView) view.findViewById(R.id.tvStatsAsocijacijeRatio)).setText("14 / 5");

        ((TextView) view.findViewById(R.id.tvStatsSkocko)).setText("5 - 25 bod.");
        ((TextView) view.findViewById(R.id.tvStatsSkockoPercent)).setText("25% / 40% / 35%");

        ((TextView) view.findViewById(R.id.tvStatsKorakPoKorak)).setText("8 - 20 bod.");
        ((TextView) view.findViewById(R.id.tvStatsKorakPercent)).setText("30% k.1 / 45% k.2");

        ((TextView) view.findViewById(R.id.tvStatsMojBroj)).setText("5 - 10 bod.");
        ((TextView) view.findViewById(R.id.tvStatsMojBrojPercent)).setText("55%");
    }
}