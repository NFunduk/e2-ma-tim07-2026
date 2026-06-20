package com.example.sabona;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.utils.NotificationHelper;
import com.example.sabona.utils.PresenceManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.example.sabona.model.AppNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private NavController navController;
    private ImageView btnNotifications;
    private TextView tvToolbarTitle;

    private FirebaseFirestore db;
    private ListenerRegistration notificationsListener;
    private boolean firstLoadNotifications = true;

    private final Set<Integer> authDestinations = new HashSet<>(Arrays.asList(
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.verifyEmailFragment
    ));

    private final Set<Integer> gameDestinations = new HashSet<>(Arrays.asList(
            R.id.koZnaZnaFragment,
            R.id.spojniceFragment,
            R.id.associationsFragment,
            R.id.skockoFragment,
            R.id.korakPoKorakFragment,
            R.id.mojBrojFragment
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


        LinearLayout layoutToolbarNormal = toolbar.findViewById(R.id.layoutToolbarNormal);
        LinearLayout layoutToolbarGame = toolbar.findViewById(R.id.layoutToolbarGame);
        TextView tvGameName = toolbar.findViewById(R.id.tvGameName);

        LinearLayout layoutStatsChip = toolbar.findViewById(R.id.layoutStatsChip);

        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
        navController = navHostFragment.getNavController();

        if (getIntent() != null &&
                getIntent().getBooleanExtra("open_notifications", false)) {
            if (navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() != R.id.notificationsFragment) {
                navController.navigate(R.id.notificationsFragment);
            }
        }

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            boolean isAuth = authDestinations.contains(destination.getId());
            boolean isGame = gameDestinations.contains(destination.getId());
            int id = destination.getId();


            toolbar.setVisibility(isAuth ? View.GONE : View.VISIBLE);

            bottomNav.setVisibility((isAuth || isGame) ? View.GONE : View.VISIBLE);

            layoutStatsChip.setVisibility(isGame ? View.GONE : View.VISIBLE);

            // Igrač je "u partiji" dok se nalazi na nekom od game ekrana —
            // koristi se da prijatelji ne mogu pozvati igrača koji je već zauzet partijom.
            PresenceManager.setInGame(isGame);

            //bottomNav.setVisibility(isAuth ? View.GONE : View.VISIBLE);

            /*bottomNav.setEnabled(!isGame);
            for (int i = 0; i < bottomNav.getMenu().size(); i++) {
                bottomNav.getMenu().getItem(i).setEnabled(!isGame);
            }*/

            //bottomNav.setVisibility(isGame ? View.GONE : View.VISIBLE);

            btnNotifications.setImageResource(isGame
                    ? R.drawable.outline_close_24
                    : R.drawable.outline_notifications_24);
            btnNotifications.setAlpha(1.0f);

            if (isGame) {
                // Prikaži game mod toolbara
                layoutToolbarNormal.setVisibility(View.GONE);
                layoutToolbarGame.setVisibility(View.VISIBLE);

                // Postavi ime igre sitno
                if (id == R.id.koZnaZnaFragment)             tvGameName.setText("Ko zna zna");
                else if (id == R.id.spojniceFragment)         tvGameName.setText("Spojnice");
                else if (id == R.id.associationsFragment)     tvGameName.setText("Asocijacije");
                else if (id == R.id.skockoFragment)           tvGameName.setText("Skočko");
                else if (id == R.id.korakPoKorakFragment)     tvGameName.setText("Korak po korak");
                else if (id == R.id.mojBrojFragment)          tvGameName.setText("Moj broj");

            } else {
                // Prikaži normalni toolbar
                layoutToolbarNormal.setVisibility(View.VISIBLE);
                layoutToolbarGame.setVisibility(View.GONE);
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
            } else if (id == R.id.friends) {
                navController.navigate(R.id.friendsFragment);
                return true;
            } else if (id == R.id.profile) {
                navController.navigate(R.id.profileFragment);
                return true;
            }
            return false;
        });

        btnNotifications.setOnClickListener(v -> {
            boolean isGame = gameDestinations.contains(
                    navController.getCurrentDestination().getId());

            if (isGame) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Odustajanje")
                        .setMessage("Da li želiš da odustaneš od partije?")
                        .setNegativeButton("Nastavi igru", null)
                        .setPositiveButton("Odustani", (dialog, which) ->
                                navController.navigate(R.id.homeFragment))
                        .show();
            } else {
                if (navController.getCurrentDestination() != null &&
                        navController.getCurrentDestination().getId() != R.id.notificationsFragment) {
                    navController.navigate(R.id.notificationsFragment);
                }
            }
        });

        NotificationHelper.createChannels(this);
        com.example.sabona.league.DailyTokenWorker.scheduleIfNeeded(this);

        db = FirebaseFirestore.getInstance();
        startListeningForSystemNotifications();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    101
            );
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Aplikacija je u prvom planu → igrač je "online" i dostupan za poziv prijatelja.
        PresenceManager.setOnline(true);

        if (notificationsListener == null &&
                FirebaseAuth.getInstance().getCurrentUser() != null) {
            firstLoadNotifications = true;
            startListeningForSystemNotifications();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Aplikacija odlazi u pozadinu → igrač više nije dostupan za novi poziv na partiju.
        PresenceManager.setOnline(false);
    }

    private void startListeningForSystemNotifications() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        if (notificationsListener != null) {
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        notificationsListener = db.collection("users")
                .document(uid)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    for (DocumentChange change : snapshots.getDocumentChanges()) {
                        if (change.getType() == DocumentChange.Type.ADDED) {
                            AppNotification notification =
                                    change.getDocument().toObject(AppNotification.class);

                            notification.setId(change.getDocument().getId());

                            if (!notification.isRead()) {
                                NotificationHelper.showNotification(
                                        MainActivity.this,
                                        notification
                                );
                            }
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (notificationsListener != null) {
            notificationsListener.remove();
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent.getBooleanExtra("open_notifications", false)
                && navController != null) {
            if (navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() != R.id.notificationsFragment) {
                navController.navigate(R.id.notificationsFragment);
            }
        }
    }
}