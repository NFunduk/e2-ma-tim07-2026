package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ProfileFragment extends Fragment {

    // Header (sticky)
    private ImageView imgAvatar;
    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvRegion;
    private TextView tvTokens;
    private TextView tvStars;
    private TextView tvLeague;
    private ImageView imgLeagueIcon;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    // Shared data za tabove (možeš zamijeniti sa ViewModel-om kad dođe backend)
    private static final String[] TAB_TITLES = {"Profil", "Statistika", "Podešavanja"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initHeaderViews(view);
        loadMockHeaderData();
        setupViewPager(view);
    }

    private void initHeaderViews(View view) {
        imgAvatar    = view.findViewById(R.id.imgAvatar);
        tvUsername   = view.findViewById(R.id.tvUsername);
        tvEmail      = view.findViewById(R.id.tvEmail);
        tvRegion     = view.findViewById(R.id.tvRegion);
        tvTokens     = view.findViewById(R.id.tvTokens);
        tvStars      = view.findViewById(R.id.tvStars);
        tvLeague     = view.findViewById(R.id.tvLeague);
        imgLeagueIcon = view.findViewById(R.id.imgLeagueIcon);
    }

    private void loadMockHeaderData() {
        tvUsername.setText("Marko123");
        tvEmail.setText("marko@example.com");
        tvRegion.setText("Vojvodina");
        tvTokens.setText("12");
        tvStars.setText("340");
        tvLeague.setText("Liga 3");
        imgLeagueIcon.setImageResource(R.drawable.star);
    }

    private void setupViewPager(View view) {
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);

        viewPager.setAdapter(new ProfilePagerAdapter(requireActivity()));

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();
    }

    // ============================================================
    //  Pager adapter — svaki tab je zaseban Fragment
    // ============================================================
    private static class ProfilePagerAdapter extends FragmentStateAdapter {

        ProfilePagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new ProfileInfoTabFragment();
                case 1: return new ProfileStatsTabFragment();
                case 2: return new ProfileSettingsTabFragment();
                default: return new ProfileInfoTabFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}