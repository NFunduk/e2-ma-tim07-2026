package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.sabona.league.League;
import com.example.sabona.viewModel.ProfileViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Map;

public class ProfileFragment extends Fragment {

    // Header views
    private ImageView imgAvatar;
    private ImageView imgAvatarBorder;
    private TextView tvUsername, tvEmail, tvRegion, tvTokens, tvStars, tvLeague;
    private ImageView imgLeagueIcon;
    private View layoutLeagueRow;

    // Guest / logged-in layouts
    private View layoutLoggedIn;
    private View layoutGuest;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    private ProfileViewModel viewModel;

    private static final String[] TAB_TITLES = {"Profil", "Statistika", "Podešavanja"};

    private static final String[] LEAGUE_NAMES = {
            "Nulta liga", "Bronze liga", "Srebrna liga",
            "Zlatna liga", "Platinasta liga", "Dijamantska liga"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);

        // Guest / logged-in containers (mogu biti isti layout, provjeri ima li ih)
        layoutLoggedIn = view.findViewById(R.id.layoutLoggedIn);
        layoutGuest    = view.findViewById(R.id.layoutGuest);

        initHeaderViews(view);

        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;

        if (isLoggedIn) {
            showLoggedInUI(view);
            // Uvijek reload pri otvaranju – sprečava stare podatke
            viewModel.loadUser();
            viewModel.loadStats();
            observeViewModel();
        } else {
            showGuestUI();
        }
    }

    private void showLoggedInUI(View view) {
        if (layoutGuest    != null) layoutGuest.setVisibility(View.GONE);
        if (layoutLoggedIn != null) layoutLoggedIn.setVisibility(View.VISIBLE);
        setupViewPager(view);
    }

    private void showGuestUI() {
        if (layoutLoggedIn != null) layoutLoggedIn.setVisibility(View.GONE);
        if (layoutGuest    != null) {
            layoutGuest.setVisibility(View.VISIBLE);
            android.widget.Button btnGuestLogin = layoutGuest.findViewById(R.id.btnGuestLogin);
            if (btnGuestLogin != null) {
                btnGuestLogin.setOnClickListener(v -> {
                    try {
                        androidx.navigation.NavController navController =
                                androidx.navigation.Navigation.findNavController(
                                        requireActivity(), R.id.navHostFragment);
                        navController.navigate(R.id.action_profile_to_login);
                    } catch (Exception e) {
                        // Ako navigacija ne uspe (npr. nepredviđeno stanje back stacka),
                        // bar obavesti korisnika umesto da dugme izgleda "mrtvo".
                        Toast.makeText(requireContext(),
                                "Greška pri otvaranju prijave, pokušaj ponovo.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void initHeaderViews(View view) {
        imgAvatar    = view.findViewById(R.id.imgAvatar);
        imgAvatarBorder = view.findViewById(R.id.imgAvatarBorder);
        tvUsername   = view.findViewById(R.id.tvUsername);
        tvEmail      = view.findViewById(R.id.tvEmail);
        tvRegion     = view.findViewById(R.id.tvRegion);
        tvTokens     = view.findViewById(R.id.tvTokens);
        tvStars      = view.findViewById(R.id.tvStars);
        tvLeague     = view.findViewById(R.id.tvLeague);
        imgLeagueIcon = view.findViewById(R.id.imgLeagueIcon);
        layoutLeagueRow = view.findViewById(R.id.layoutLeagueRow);
    }

    private void observeViewModel() {
        // Avatar update – odmah osvježi sliku u headeru
        viewModel.getAvatarUpdated().observe(getViewLifecycleOwner(), updated -> {
            if (Boolean.TRUE.equals(updated)) {
                Map<String, Object> data = viewModel.getUserData().getValue();
                if (data != null) {
                    String avatarRes = getStr(data, "avatarRes", null);
                    if (avatarRes != null) {
                        int resId = resolveAvatar(avatarRes);
                        if (resId != 0 && imgAvatar != null) imgAvatar.setImageResource(resId);
                    }
                }
                viewModel.resetAvatarUpdated();
            }
        });

        // Error observer
        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });

        // Podaci korisnika
        viewModel.getUserData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;

            if (tvUsername != null) tvUsername.setText(getStr(data, "username", "—"));
            if (tvEmail    != null) tvEmail.setText(getStr(data, "email", "—"));
            if (tvRegion   != null) tvRegion.setText(getStr(data, "region", "—"));

            // Specifikacija 5.e — okvir avatara u zlatnoj/srebrnoj/bronzanoj
            // boji ako je region igrača bio 1./2./3. na PRETHODNOM (najskorije
            // arhiviranom) mesečnom ciklusu regiona.
            applyAvatarBorderForRegion(getStr(data, "region", null));

            long tokens = getLong(data, "tokens", 0);
            long stars  = getLong(data, "stars", 0);
            int  leagueIndex = (int) getLong(data, "league", 0);

            if (tvTokens != null) tvTokens.setText(String.valueOf(tokens));
            if (tvStars  != null) tvStars.setText(String.valueOf(stars));

            // Koristimo League enum za naziv i ikonu
            League league = League.fromIndex(leagueIndex);
            if (tvLeague != null) tvLeague.setText(league.displayName);

            // Postavi ikonu lige – svaka liga ima svoju boju da se razlikuje na profilu
            if (imgLeagueIcon != null) {
                int iconResId = getResources().getIdentifier(
                        league.iconResName, "drawable", requireContext().getPackageName());
                if (iconResId != 0) {
                    imgLeagueIcon.setImageResource(iconResId);
                }
                imgLeagueIcon.setColorFilter(leagueAccentColor(league.index));
            }

            // Klik na cijeli red (ikonica + naziv + strelica) → otvori ekran napretka
            if (layoutLeagueRow != null) {
                layoutLeagueRow.setOnClickListener(v ->
                        androidx.navigation.Navigation.findNavController(
                                        requireActivity(), R.id.navHostFragment)
                                .navigate(R.id.action_profile_to_league));

                // Dugi pritisak na red lige → meni za testiranje napredovanja kroz lige
                // (samo u debug buildu, ne ide u produkciju)
                boolean isDebugBuild = (requireContext().getApplicationInfo().flags
                        & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                if (isDebugBuild) {
                    layoutLeagueRow.setOnLongClickListener(v -> {
                        com.example.sabona.league.LeagueTestMenu.show(
                                requireContext(), getChildFragmentManager());
                        return true;
                    });
                }
            }

            String avatarRes = getStr(data, "avatarRes", null);
            if (avatarRes != null && imgAvatar != null) {
                int resId = resolveAvatar(avatarRes);
                if (resId != 0) imgAvatar.setImageResource(resId);
            }
        });
    }

    // ─── Boje liga ────────────────────────────────────────────────────────────
    /** Vraća akcentnu boju za ikonu lige u headeru profila (po indeksu 0–5). */
    private int leagueAccentColor(int leagueIndex) {
        switch (leagueIndex) {
            case 1:  return 0xFFCD7F32; // Bronzana
            case 2:  return 0xFFC0C0C0; // Srebrna
            case 3:  return 0xFFFFD700; // Zlatna
            case 4:  return 0xFF00BCD4; // Platinska
            case 5:  return 0xFFCE93D8; // Dijamantska
            default: return 0xFFBDBDBD; // Nulta
        }
    }

    // ─── Okvir avatara po regionu (specifikacija 5.e) ───────────────────────────
    /**
     * Ako je region igrača bio 1./2./3. na PRETHODNOM (poslednjem
     * arhiviranom) mesečnom ciklusu rang liste regiona, oboji
     * {@code imgAvatarBorder} zlatnom/srebrnom/bronzanom bojom. Ako region
     * nije bio u top 3 (ili arhiva ne postoji još, npr. nijedan ciklus
     * još nije završen), vrati okvir na neutralnu boju.
     */
    private void applyAvatarBorderForRegion(String regionName) {
        if (imgAvatarBorder == null) return;

        com.example.sabona.region.SerbianRegion region =
                com.example.sabona.region.SerbianRegion.fromDisplayName(regionName);
        if (region == null) {
            imgAvatarBorder.setColorFilter(0xFFBDBDBD); // nema region → neutralna boja
            return;
        }

        new com.example.sabona.region.RegionRepository().loadLastCycleResultForRegion(
                region,
                new com.example.sabona.region.RegionRepository.LastCycleResultCallback() {
                    @Override
                    public void onSuccess(com.example.sabona.region.RegionCycleResult result) {
                        if (!isAdded() || imgAvatarBorder == null) return; // fragment je u međuvremenu uklonjen

                        if (result == null) {
                            imgAvatarBorder.setColorFilter(0xFFBDBDBD); // region nije bio u top 3
                            return;
                        }

                        switch (result.position) {
                            case 1: imgAvatarBorder.setColorFilter(0xFFFFD700); break; // zlatna
                            case 2: imgAvatarBorder.setColorFilter(0xFFC0C0C0); break; // srebrna
                            case 3: imgAvatarBorder.setColorFilter(0xFFCD7F32); break; // bronzana
                            default: imgAvatarBorder.setColorFilter(0xFFBDBDBD);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // Tiho zadrži postojeću/neutralnu boju — okvir avatara
                        // nije kritična funkcionalnost da bismo prekidali korisnika
                        // toast porukom zbog greške u učitavanju.
                    }
                });
    }

    // ─── Avatar ────────────────────────────────────────────────────────────────
    private int resolveAvatar(String name) {
        switch (name) {
            case "gallery":  return android.R.drawable.ic_menu_gallery;
            case "places":   return android.R.drawable.ic_menu_myplaces;
            case "compass":  return android.R.drawable.ic_menu_compass;
            case "camera":   return android.R.drawable.ic_menu_camera;
            case "manage":   return android.R.drawable.ic_menu_manage;
            case "search":   return android.R.drawable.ic_menu_search;
            case "share":    return android.R.drawable.ic_menu_share;
            case "send":     return android.R.drawable.ic_menu_send;
            case "add":      return android.R.drawable.ic_menu_add;
            case "info":     return android.R.drawable.ic_menu_info_details;
            case "edit":     return android.R.drawable.ic_menu_edit;
            case "view":     return android.R.drawable.ic_menu_view;
            default:         return 0;
        }
    }

    // ─── ViewPager ─────────────────────────────────────────────────────────────
    private void setupViewPager(View view) {
        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);
        if (viewPager == null || tabLayout == null) return;
        viewPager.setAdapter(new ProfilePagerAdapter(requireActivity()));
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, pos) -> tab.setText(TAB_TITLES[pos])).attach();
    }

    // ─── Utils ─────────────────────────────────────────────────────────────────
    private String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v instanceof String) ? (String) v : def;
    }

    private long getLong(Map<String, Object> map, String key, long def) {
        Object v = map.get(key);
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Double)  return ((Double) v).longValue();
        return def;
    }

    // ─── Adapter ───────────────────────────────────────────────────────────────
    private static class ProfilePagerAdapter extends FragmentStateAdapter {
        ProfilePagerAdapter(@NonNull FragmentActivity fa) { super(fa); }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:  return new ProfileInfoTabFragment();
                case 1:  return new ProfileStatsTabFragment();
                case 2:  return new ProfileSettingsTabFragment();
                default: return new ProfileInfoTabFragment();
            }
        }

        @Override
        public int getItemCount() { return 3; }
    }
}