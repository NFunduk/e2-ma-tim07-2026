package com.example.sabona.leaderboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.R;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class LeaderboardFragment extends Fragment {

    private LeaderboardViewModel viewModel;
    private LeaderboardAdapter adapter;

    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvCycleRange;
    private TextView tvEmpty;
    private TextView tvLeaderboardTitle;

    private boolean showingWeekly = true;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewModel != null) {
                viewModel.loadAll();
            }
            handler.postDelayed(this, 120000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(LeaderboardViewModel.class);

        initViews(view);
        setupRecycler();
        setupTabs();
        observeData();

        tvCycleRange.setText(getWeeklyRangeText());
        viewModel.loadAll();
        handler.postDelayed(refreshRunnable, 120000);

        tvLeaderboardTitle.setOnLongClickListener(v -> {
            LeaderboardRepository repo = new LeaderboardRepository();

            if (showingWeekly) {
                repo.distributeWeeklyRewards();
                Toast.makeText(requireContext(), "Test: podeljene nedeljne nagrade", Toast.LENGTH_SHORT).show();
            } else {
                repo.distributeMonthlyRewards();
                Toast.makeText(requireContext(), "Test: podeljene mesečne nagrade", Toast.LENGTH_SHORT).show();
            }

            return true;
        });
    }

    private void initViews(View view) {
        tabLayout = view.findViewById(R.id.tabLeaderboard);
        recyclerView = view.findViewById(R.id.recyclerLeaderboard);
        progressBar = view.findViewById(R.id.progressLeaderboard);
        tvCycleRange = view.findViewById(R.id.tvCycleRange);
        tvEmpty = view.findViewById(R.id.tvEmptyLeaderboard);
        tvLeaderboardTitle = view.findViewById(R.id.tvLeaderboardTitle);
    }

    private void setupRecycler() {
        adapter = new LeaderboardAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Nedeljna"));
        tabLayout.addTab(tabLayout.newTab().setText("Mesečna"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showingWeekly = tab.getPosition() == 0;

                if (showingWeekly) {
                    tvLeaderboardTitle.setText("Nedeljna rang lista");
                    tvCycleRange.setText(getWeeklyRangeText());
                    if (viewModel.getWeeklyEntries().getValue() != null) {
                        adapter.submitList(viewModel.getWeeklyEntries().getValue());
                        updateEmptyState(viewModel.getWeeklyEntries().getValue().isEmpty());
                    }
                } else {
                    tvLeaderboardTitle.setText("Mesečna rang lista");
                    tvCycleRange.setText(getMonthlyRangeText());
                    if (viewModel.getMonthlyEntries().getValue() != null) {
                        adapter.submitList(viewModel.getMonthlyEntries().getValue());
                        updateEmptyState(viewModel.getMonthlyEntries().getValue().isEmpty());
                    }
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void observeData() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        viewModel.getWeeklyEntries().observe(getViewLifecycleOwner(), entries -> {
            if (showingWeekly) {
                adapter.submitList(entries);
                updateEmptyState(entries == null || entries.isEmpty());
            }
        });

        viewModel.getMonthlyEntries().observe(getViewLifecycleOwner(), entries -> {
            if (!showingWeekly) {
                adapter.submitList(entries);
                updateEmptyState(entries == null || entries.isEmpty());
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateEmptyState(boolean isEmpty) {
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private String getWeeklyRangeText() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        Calendar start = (Calendar) calendar.clone();

        calendar.add(Calendar.DAY_OF_WEEK, 6);
        Calendar end = (Calendar) calendar.clone();

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy.", Locale.getDefault());
        return "Ciklus: " + sdf.format(start.getTime()) + " - " + sdf.format(end.getTime());
    }

    private String getMonthlyRangeText() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Calendar start = (Calendar) calendar.clone();

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        Calendar end = (Calendar) calendar.clone();

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy.", Locale.getDefault());
        return "Ciklus: " + sdf.format(start.getTime()) + " - " + sdf.format(end.getTime());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(refreshRunnable);
    }
}