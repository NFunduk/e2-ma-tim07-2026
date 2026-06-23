package com.example.sabona.leaderboard;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LeaderboardCycleWorker extends Worker {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LeaderboardCycleWorker(@NonNull Context context,
                                  @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            processWeeklyIfNeeded();
            processMonthlyIfNeeded();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void processWeeklyIfNeeded() throws Exception {
        Calendar now = Calendar.getInstance();

        // Nedeljni ciklus završavamo ponedeljkom.
        int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek != Calendar.MONDAY) return;

        String previousWeekId = getPreviousWeekCycleId();
        String docId = "weekly_" + previousWeekId;

        DocumentReference cycleRef = db.collection("leaderboardProcessedCycles")
                .document(docId);

        Boolean alreadyProcessed = Tasks.await(db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(cycleRef);

            if (snap.exists()) {
                return true;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("type", "weekly");
            data.put("cycleId", previousWeekId);
            data.put("processedAt", Timestamp.now());

            transaction.set(cycleRef, data);
            return false;
        }));

        if (Boolean.TRUE.equals(alreadyProcessed)) return;

        LeaderboardRepository repo = new LeaderboardRepository();

        Tasks.await(repo.distributeWeeklyRewardsTask());
        Tasks.await(repo.resetWeeklyCycleTask());
    }

    private void processMonthlyIfNeeded() throws Exception {
        Calendar now = Calendar.getInstance();

        // Mesečni ciklus završavamo prvog dana u mesecu.
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        if (dayOfMonth != 1) return;

        String previousMonthId = getPreviousMonthCycleId();
        String docId = "monthly_" + previousMonthId;

        DocumentReference cycleRef = db.collection("leaderboardProcessedCycles")
                .document(docId);

        Boolean alreadyProcessed = Tasks.await(db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(cycleRef);

            if (snap.exists()) {
                return true;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("type", "monthly");
            data.put("cycleId", previousMonthId);
            data.put("processedAt", Timestamp.now());

            transaction.set(cycleRef, data);
            return false;
        }));

        if (Boolean.TRUE.equals(alreadyProcessed)) return;

        LeaderboardRepository repo = new LeaderboardRepository();

        Tasks.await(repo.distributeMonthlyRewardsTask());
        Tasks.await(repo.resetMonthlyCycleTask());
    }

    private String getPreviousWeekCycleId() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.WEEK_OF_YEAR, -1);

        int year = calendar.get(Calendar.YEAR);
        int week = calendar.get(Calendar.WEEK_OF_YEAR);

        return year + "_W" + week;
    }

    private String getPreviousMonthCycleId() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -1);

        SimpleDateFormat format = new SimpleDateFormat("yyyy_MM", Locale.getDefault());
        return format.format(calendar.getTime());
    }

    public static void scheduleIfNeeded(Context context) {
        androidx.work.PeriodicWorkRequest request =
                new androidx.work.PeriodicWorkRequest.Builder(
                        LeaderboardCycleWorker.class,
                        12,
                        java.util.concurrent.TimeUnit.HOURS
                ).build();

        androidx.work.WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "leaderboard_cycle_worker",
                        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }
}