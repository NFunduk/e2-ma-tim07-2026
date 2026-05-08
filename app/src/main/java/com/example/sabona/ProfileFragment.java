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
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputEditText;

public class ProfileFragment extends Fragment {

    // Header podaci
    private ImageView imgAvatar;
    private Button btnChangeAvatar;
    private TextView tvUsername;
    private TextView tvEmail;
    private TextView tvRegion;
    private TextView tvTokens;
    private TextView tvStars;
    private TextView tvLeague;
    private ImageView imgQrCode;

    // Statistika
    private TextView tvTotalGames;
    private TextView tvWinPercent;
    private TextView tvLossPercent;

    // Statistika po igrama
    private TextView tvStatsKoZnaZna;
    private TextView tvStatsSpojnice;
    private TextView tvStatsAsocijacije;
    private TextView tvStatsSkocko;
    private TextView tvStatsKorakPoKorak;
    private TextView tvStatsMojBroj;

    // Promjena lozinke
    private TextInputEditText etOldPassword;
    private TextInputEditText etNewPassword;
    private TextInputEditText etConfirmPassword;
    private Button btnSavePassword;

    // Logout
    private Button btnLogout;

    // Detaljna statistika
    private TextView tvStatsKzzRatio;
    private TextView tvStatsSpojnicePercent;
    private TextView tvStatsAsocijacijeRatio;
    private TextView tvStatsSkockoPercent;
    private TextView tvStatsKorakPercent;
    private TextView tvStatsMojBrojPercent;
    private ImageView imgLeagueIcon;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        loadMockData();
        setupListeners();
    }

    private void initViews(View view) {
        imgAvatar           = view.findViewById(R.id.imgAvatar);
        btnChangeAvatar     = view.findViewById(R.id.btnChangeAvatar);
        tvUsername          = view.findViewById(R.id.tvUsername);
        tvEmail             = view.findViewById(R.id.tvEmail);
        tvRegion            = view.findViewById(R.id.tvRegion);
        tvTokens            = view.findViewById(R.id.tvTokens);
        tvStars             = view.findViewById(R.id.tvStars);
        tvLeague            = view.findViewById(R.id.tvLeague);
        imgQrCode           = view.findViewById(R.id.imgQrCode);

        tvTotalGames        = view.findViewById(R.id.tvTotalGames);
        tvWinPercent        = view.findViewById(R.id.tvWinPercent);
        tvLossPercent       = view.findViewById(R.id.tvLossPercent);

        tvStatsKoZnaZna     = view.findViewById(R.id.tvStatsKoZnaZna);
        tvStatsSpojnice     = view.findViewById(R.id.tvStatsSpojnice);
        tvStatsAsocijacije  = view.findViewById(R.id.tvStatsAsocijacije);
        tvStatsSkocko       = view.findViewById(R.id.tvStatsSkocko);
        tvStatsKorakPoKorak = view.findViewById(R.id.tvStatsKorakPoKorak);
        tvStatsMojBroj      = view.findViewById(R.id.tvStatsMojBroj);

        etOldPassword       = view.findViewById(R.id.etOldPassword);
        etNewPassword       = view.findViewById(R.id.etNewPassword);
        etConfirmPassword   = view.findViewById(R.id.etConfirmPassword);
        btnSavePassword     = view.findViewById(R.id.btnSavePassword);

        btnLogout           = view.findViewById(R.id.btnLogout);

        tvStatsKzzRatio         = view.findViewById(R.id.tvStatsKzzRatio);
        tvStatsSpojnicePercent  = view.findViewById(R.id.tvStatsSpojnicePercent);
        tvStatsAsocijacijeRatio = view.findViewById(R.id.tvStatsAsocijacijeRatio);
        tvStatsSkockoPercent    = view.findViewById(R.id.tvStatsSkockoPercent);
        tvStatsKorakPercent     = view.findViewById(R.id.tvStatsKorakPercent);
        tvStatsMojBrojPercent   = view.findViewById(R.id.tvStatsMojBrojPercent);
        imgLeagueIcon           = view.findViewById(R.id.imgLeagueIcon);
    }

    private void loadMockData() {
        tvUsername.setText("Marko123");
        tvEmail.setText("marko@example.com");
        tvRegion.setText("Vojvodina");
        tvTokens.setText("12");
        tvStars.setText("340");
        tvLeague.setText("Liga 3");

        tvTotalGames.setText("87");
        tvWinPercent.setText("62%");
        tvLossPercent.setText("38%");

        tvStatsKoZnaZna.setText("15 - 35 bod.");
        tvStatsSpojnice.setText("8 - 16 bod.");
        tvStatsAsocijacije.setText("10 - 40 bod.");
        tvStatsSkocko.setText("5 - 25 bod.");
        tvStatsKorakPoKorak.setText("8 - 20 bod.");
        tvStatsMojBroj.setText("5 - 10 bod.");

        tvStatsKzzRatio.setText("32 / 8");
        tvStatsSpojnicePercent.setText("78%");
        tvStatsAsocijacijeRatio.setText("14 / 5");
        tvStatsSkockoPercent.setText("25% / 40% / 35%");
        tvStatsKorakPercent.setText("30% k.1 / 45% k.2");
        tvStatsMojBrojPercent.setText("55%");
        imgLeagueIcon.setImageResource(R.drawable.star);
    }

    private void setupListeners() {
        btnChangeAvatar.setOnClickListener(v -> {
            // TODO: Otvoriti picker za avatar
        });

        btnSavePassword.setOnClickListener(v -> {
            String oldPass = etOldPassword.getText() != null
                    ? etOldPassword.getText().toString().trim() : "";
            String newPass = etNewPassword.getText() != null
                    ? etNewPassword.getText().toString().trim() : "";
            String confirmPass = etConfirmPassword.getText() != null
                    ? etConfirmPassword.getText().toString().trim() : "";

            if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(requireContext(), "Popuni sva polja!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirmPass)) {
                Toast.makeText(requireContext(), "Lozinke se ne podudaraju!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPass.length() < 6) {
                Toast.makeText(requireContext(), "Lozinka je prekratka!", Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: Pozvati logiku za promjenu lozinke
            Toast.makeText(requireContext(), "Lozinka promijenjena!", Toast.LENGTH_SHORT).show();

            etOldPassword.setText("");
            etNewPassword.setText("");
            etConfirmPassword.setText("");
        });

        btnLogout.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Odjavljeni ste!", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_profile_to_login);
        });
    }
}