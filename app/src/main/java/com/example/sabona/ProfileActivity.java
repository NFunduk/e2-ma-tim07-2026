package com.example.sabona;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;

public class ProfileActivity extends AppCompatActivity {

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
    private ImageView btnLogout;

    // Bottom nav
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        loadMockData();
        setupListeners();
        setupBottomNavigation();
    }

    private void initViews() {
        imgAvatar = findViewById(R.id.imgAvatar);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        tvUsername = findViewById(R.id.tvUsername);
        tvEmail = findViewById(R.id.tvEmail);
        tvRegion = findViewById(R.id.tvRegion);
        tvTokens = findViewById(R.id.tvTokens);
        tvStars = findViewById(R.id.tvStars);
        tvLeague = findViewById(R.id.tvLeague);
        imgQrCode = findViewById(R.id.imgQrCode);

        tvTotalGames = findViewById(R.id.tvTotalGames);
        tvWinPercent = findViewById(R.id.tvWinPercent);
        tvLossPercent = findViewById(R.id.tvLossPercent);

        tvStatsKoZnaZna = findViewById(R.id.tvStatsKoZnaZna);
        tvStatsSpojnice = findViewById(R.id.tvStatsSpojnice);
        tvStatsAsocijacije = findViewById(R.id.tvStatsAsocijacije);
        tvStatsSkocko = findViewById(R.id.tvStatsSkocko);
        tvStatsKorakPoKorak = findViewById(R.id.tvStatsKorakPoKorak);
        tvStatsMojBroj = findViewById(R.id.tvStatsMojBroj);

        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSavePassword = findViewById(R.id.btnSavePassword);

        btnLogout = findViewById(R.id.btnLogout);
        bottomNav = findViewById(R.id.bottomNav);
    }

    /**
     * Mokap podaci - zamijeniti sa pravim podacima iz baze
     */
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

        // Statistika po igrama (opseg prosjecno osvojenih bodova)
        tvStatsKoZnaZna.setText("15 - 35 bod.");
        tvStatsSpojnice.setText("8 - 16 bod.");
        tvStatsAsocijacije.setText("10 - 40 bod.");
        tvStatsSkocko.setText("5 - 25 bod.");
        tvStatsKorakPoKorak.setText("8 - 20 bod.");
        tvStatsMojBroj.setText("5 - 10 bod.");
    }

    private void setupListeners() {
        btnChangeAvatar.setOnClickListener(v -> {
            // TODO: Otvoriti picker za avatar
            //Toast.makeText(this, getString(R.string.avatar_change_soon), Toast.LENGTH_SHORT).show();
        });

        btnSavePassword.setOnClickListener(v -> {
            String oldPass = etOldPassword.getText() != null
                    ? etOldPassword.getText().toString().trim() : "";
            String newPass = etNewPassword.getText() != null
                    ? etNewPassword.getText().toString().trim() : "";
            String confirmPass = etConfirmPassword.getText() != null
                    ? etConfirmPassword.getText().toString().trim() : "";

            if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                //Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirmPass)) {
                //Toast.makeText(this, getString(R.string.passwords_dont_match), Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPass.length() < 6) {
                //Toast.makeText(this, getString(R.string.password_too_short), Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: Pozvati logiku za promjenu lozinke
            //Toast.makeText(this, getString(R.string.password_changed_success), Toast.LENGTH_SHORT).show();

            etOldPassword.setText("");
            etNewPassword.setText("");
            etConfirmPassword.setText("");
        });

        btnLogout.setOnClickListener(v -> {
            // TODO: Implementirati logout logiku
            //Toast.makeText(this, getString(R.string.logged_out), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void setupBottomNavigation() {
        bottomNav.setSelectedItemId(R.id.profile);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.home) {
                finish(); // Idi na MainActivity
                return true;
            } else if (id == R.id.profile) {
                return true; // Vec smo ovdje
            }
            // TODO: Dodati navigaciju na ostale stranice kad budu gotove
            return false;
        });
    }
}