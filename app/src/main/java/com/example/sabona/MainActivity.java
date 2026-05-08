package com.example.sabona;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private NavController navController;

    private TextView tvToolbarTitle;

    private ImageView btnNotifications;

    private final Set<Integer> authDestinations = new HashSet<>(Arrays.asList(
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.verifyEmailFragment
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bottomNav = findViewById(R.id.bottomNav);
        btnNotifications = findViewById(R.id.btnNotifications);
        Toolbar toolbar = findViewById(R.id.toolbar);

        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);

        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
        navController = navHostFragment.getNavController();

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {

            boolean isAuth = authDestinations.contains(destination.getId());

            toolbar.setVisibility(isAuth ? View.GONE : View.VISIBLE);
            bottomNav.setVisibility(isAuth ? View.GONE : View.VISIBLE);

            int id = destination.getId();

            boolean isGame =
                    id == R.id.koZnaZnaFragment ||
                            id == R.id.spojniceFragment ||
                            id == R.id.associationsFragment ||
                            id == R.id.skockoFragment ||
                            id == R.id.korakPoKorakFragment ||
                            id == R.id.mojBrojFragment;

            if (isGame) {
                btnNotifications.setImageResource(R.drawable.outline_close_24);
            } else {
                btnNotifications.setImageResource(R.drawable.outline_notifications_24);
            }

            if (id == R.id.koZnaZnaFragment) {
                tvToolbarTitle.setText("Ko zna zna");
            }
            else if (id == R.id.spojniceFragment) {
                tvToolbarTitle.setText("Spojnice");
            }
            else if (id == R.id.associationsFragment) {
                tvToolbarTitle.setText("Asocijacije");
            }
            else if (id == R.id.skockoFragment) {
                tvToolbarTitle.setText("Skočko");
            }
            else if (id == R.id.korakPoKorakFragment) {
                tvToolbarTitle.setText("Korak po korak");
            }
            else if (id == R.id.mojBrojFragment) {
                tvToolbarTitle.setText("Moj broj");
            }
            else {
                tvToolbarTitle.setText("SaBoNa");
            }
        });

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.home) {
                navController.navigate(R.id.homeFragment);
                return true;
            } else if (id == R.id.play) {
                navController.navigate(R.id.koZnaZnaFragment);
                return true;
            } else if (id == R.id.profile) {
                navController.navigate(R.id.profileFragment);
                return true;
            }
            return false;
        });

        btnNotifications.setOnClickListener(v -> {

            int currentId = navController.getCurrentDestination().getId();

            boolean isGame =
                    currentId == R.id.koZnaZnaFragment ||
                            currentId == R.id.spojniceFragment ||
                            currentId == R.id.associationsFragment ||
                            currentId == R.id.skockoFragment ||
                            currentId == R.id.korakPoKorakFragment ||
                            currentId == R.id.mojBrojFragment;

            if (isGame) {

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Odustajanje")
                        .setMessage("Da li želiš da odustaneš?")
                        .setNegativeButton("Nastavi igru", null)
                        .setPositiveButton("Odustani", (dialog, which) -> {
                            navController.navigate(R.id.homeFragment);
                        })
                        .show();

            } else {
                navController.navigate(R.id.notificationsFragment);
            }
        });


    }
}