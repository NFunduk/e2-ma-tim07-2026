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

import com.example.sabona.league.League;
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
    private ListenerRegistration userStatsListener;
    private boolean firstLoadNotifications = true;
    private FirebaseAuth.AuthStateListener authStateListener;

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

        bottomNav = findViewById(R.id.bottomNav);
        btnNotifications = findViewById(R.id.btnNotifications);
        Toolbar toolbar = findViewById(R.id.toolbar);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);

        // ── Window insets (edge-to-edge) ────────────────────────────────────
        // Root layout dobija padding samo gore/lijevo/desno (status bar, notch, itd).
        // Donji sistemski razmak (navigation bar) NE ide na root, nego direktno
        // na BottomNavigationView ispod – tako traka ide do samog dna ekrana,
        // bez praznog "praznog reda" obojenog pozadinom root layouta.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // Bottom nav dobija svoj donji inset kao padding – traka i dalje ostaje
        // potpuno klikabilna i vidljiva iznad sistemske navigacije.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });


        LinearLayout layoutToolbarNormal = toolbar.findViewById(R.id.layoutToolbarNormal);
        LinearLayout layoutToolbarGame = toolbar.findViewById(R.id.layoutToolbarGame);
        TextView tvGameName = toolbar.findViewById(R.id.tvGameName);

        LinearLayout layoutStatsChip = toolbar.findViewById(R.id.layoutStatsChip);
        TextView tvStarsChip  = toolbar.findViewById(R.id.tvStarsChip);
        TextView tvTokensChip = toolbar.findViewById(R.id.tvTokensChip);
        TextView tvLeagueChip = toolbar.findViewById(R.id.tvLeagueChip);

        // Toolbar chip prikazuje uvijek aktuelne zvezde / tokene / ligu
        // ulogovanog igrača, učitane iz Firestore-a u real-time (osvježi se
        // čim se nešto promijeni, npr. odmah nakon odigrane partije).
        startListeningForUserStats(tvStarsChip, tvTokensChip, tvLeagueChip);

        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
        navController = navHostFragment.getNavController();

        // Ako je korisnik već ulogovan (i email mu je potvrđen), preskoči login
        // ekran i idi direktno na Home – ali SAMO pri prvom pokretanju Activity-ja
        // (savedInstanceState == null), ne pri svakoj rekonstrukciji (npr. rotacija
        // ekrana). Ova provjera namjerno NE postoji u LoginFragment-u – tamo bi
        // mogla pogrešno da se okine i odmah nakon logout-a (Firebase Auth state
        // ponekad treba trenutak da se ažurira na svim slušaocima), vraćajući
        // korisnika nazad na Home kao da se logout nikad nije ni desio.
        // Start destinacija grafa je uvijek loginFragment, pa ovdje ne treba
        // dodatno provjeravati getCurrentDestination() (može biti null u ovom
        // tačnom trenutku pri hladnom startu).
        if (savedInstanceState == null) {
            com.google.firebase.auth.FirebaseUser current =
                    FirebaseAuth.getInstance().getCurrentUser();
            if (current != null && current.isEmailVerified()) {
                navController.navigate(R.id.action_login_to_home);
            }
        }

        if (getIntent() != null &&
                getIntent().getBooleanExtra("open_notifications", false)) {
            if (navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() != R.id.notificationsFragment) {
                navController.navigate(R.id.notificationsFragment);
            }
        }

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            android.util.Log.d("NAV_DEBUG", "Destinacija promijenjena → " + destination.getLabel()
                    + " (id=" + destination.getId() + ")");

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
            // Zaštita: ako smo trenutno na auth ekranu (login/register/verify),
            // ignoriši svaki klik/auto-trigger sa bottomNav-a – ta traka ne bi
            // trebalo da bude vidljiva niti aktivna na tim ekranima.
            if (navController.getCurrentDestination() != null
                    && authDestinations.contains(navController.getCurrentDestination().getId())) {
                return false;
            }

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

        // Kad se korisnik odjavi (logout) – zaustavi Firestore listener za notifikacije
        // umjesto da nastavi da "visi" do uništenja Activity-ja.
        authStateListener = firebaseAuth -> {
            if (firebaseAuth.getCurrentUser() == null) {
                if (notificationsListener != null) {
                    notificationsListener.remove();
                    notificationsListener = null;
                }
                if (userStatsListener != null) {
                    userStatsListener.remove();
                    userStatsListener = null;
                }
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);

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

    /**
     * Prati u realnom vremenu dokument trenutnog korisnika i osvježava
     * toolbar chip (zvezde / tokeni / liga) čim se nešto promijeni u bazi –
     * npr. odmah nakon odigrane partije ili dnevnog bonusa tokena.
     */
    private void startListeningForUserStats(TextView tvStarsChip,
                                            TextView tvTokensChip,
                                            TextView tvLeagueChip) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        userStatsListener = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;

                    long stars  = snapshot.contains("stars")  ? snapshot.getLong("stars")  : 0;
                    long tokens = snapshot.contains("tokens") ? snapshot.getLong("tokens") : 0;
                    int leagueIndex = snapshot.contains("league")
                            ? snapshot.getLong("league").intValue() : 0;

                    League league = League.fromIndex(leagueIndex);

                    if (tvStarsChip  != null) tvStarsChip.setText(String.valueOf(stars));
                    if (tvTokensChip != null) tvTokensChip.setText(String.valueOf(tokens));
                    if (tvLeagueChip != null) tvLeagueChip.setText(league.displayName);
                });
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
        if (userStatsListener != null) {
            userStatsListener.remove();
        }
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
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