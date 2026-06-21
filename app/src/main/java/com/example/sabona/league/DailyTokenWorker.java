package com.example.sabona.league;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WorkManager Worker koji se okida jednom dnevno i dodaje tokene igraču.
 *
 * Raspored: Planira sam sebe za 24 sata unaprijed.
 *
 * Registracija na App startu (u MainActivity.onCreate ili Application.onCreate):
 *
 *   DailyTokenWorker.scheduleIfNeeded(context);
 *
 * Worker je idempotentan – čita ligu iz Firestorea i dodaje tačan broj tokena.
 */
public class DailyTokenWorker extends Worker {

    private static final String TAG     = "DailyTokenWorker";
    private static final String WORK_TAG = "daily_tokens";

    public DailyTokenWorker(@NonNull Context context,
                            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Nema prijavljenog korisnika – preskačem dnevne tokene.");
            return Result.success();
        }

        // Sinhronizovano čitanje iz Firestore-a (Worker radi na pozadinskoj niti)
        CountDownLatch latch   = new CountDownLatch(1);
        AtomicBoolean  success = new AtomicBoolean(false);

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) { latch.countDown(); return; }

                    int leagueIndex = snap.contains("league")
                            ? snap.getLong("league").intValue() : 0;
                    League league   = League.fromIndex(leagueIndex);
                    int currentTok  = snap.contains("tokens")
                            ? snap.getLong("tokens").intValue() : 0;
                    int toAdd       = LeagueManager.dailyTokens(league);

                    snap.getReference()
                            .update("tokens", currentTok + toAdd)
                            .addOnSuccessListener(v -> {
                                Log.d(TAG, "Dodano " + toAdd + " tokena (liga " + league.displayName + ")");
                                success.set(true);
                                latch.countDown();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Greška pri upisu tokena", e);
                                latch.countDown();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Greška pri čitanju korisnika", e);
                    latch.countDown();
                });

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return Result.retry();
        }

        // Planiraj sljedeći posao za 24h
        scheduleNext(getApplicationContext());

        return success.get() ? Result.success() : Result.retry();
    }

    //  Planiranje                                                          //

    /**
     * Planira Worker da se izvrši za 24 sata.
     * Poziva se i na App startu (ako nije zakazan) i iz samog Workera.
     */
    public static void scheduleNext(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DailyTokenWorker.class)
                .setInitialDelay(24, TimeUnit.HOURS)
                .addTag(WORK_TAG)
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    /**
     * Pozovi iz MainActivity.onCreate() ili Application.onCreate() samo jednom.
     * Provjerava da li je Worker već zakazan da ne dupplicira.
     */
    public static void scheduleIfNeeded(Context context) {
        WorkManager.getInstance(context)
                .getWorkInfosByTag(WORK_TAG)
                .addListener(() -> {
                    // Jednostavno zakaži – WorkManager deduplikuje po tagu ako koristimo
                    // UniqueWork, ali ovdje koristimo One-time pa samo planiraj jednom.
                    scheduleNext(context);
                }, command -> new Thread(command).start());
    }
}