package com.example.sabona.league;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;

/**
 * Meni za ručno testiranje funkcionalnosti napredovanja kroz lige.
 *
 * Dostupan SAMO u debug buildu, preko dugog pritiska
 * na red lige u Profilu (ikonica + naziv lige).
 *
 * Svi rezultati se prikazuju direktno na telefonu (Toast/Dialog) –
 * Ne dira produkcijski kod – čisto za ručnu QA provjeru pred odbranu.
 */
public final class LeagueTestMenu {

    private LeagueTestMenu() {}

    public static void show(Context context, FragmentManager fragmentManager) {
        String[] options = new String[]{
                "b) Dnevni bonus tokeni (league=3, tokens=10 → očekuje 18)",
                "c) Pragovi liga (prikaz svih granica na ekranu)",
                "d) Napredak u ligu (stars=95 +15 → Bronzana + dijalog)",
                "e) Mjesečna kazna 30% (stars=430, league=3 → 301 + Srebrna)"
        };

        new AlertDialog.Builder(context)
                .setTitle("🧪 Test: Napredovanje kroz lige")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: testDailyTokens(context);   break;
                        case 1: testThresholds(context);    break;
                        case 2: testLeagueUp(context);      break;
                        case 3: testMonthlyPenalty(context); break;
                    }
                })
                .setNegativeButton("Zatvori", null)
                .show();
    }

    // ── b) Dnevni bonus tokeni ──────────────────────────────────────────
    private static void testDailyTokens(Context context) {
        // Pretpostavka: u Firestoru ručno postavljeno league=3, tokens=10
        new LeagueRepository().grantDailyTokens(
                League.fromIndex(3),
                new LeagueRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(context,
                                "TEST b) OK – provjeri tokens=18 u Firestoru",
                                Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onError(String msg) {
                        Toast.makeText(context, "Greška: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── c) Provjera pragova liga ────────────────────────────────────────
    private static void testThresholds(Context context) {
        int[] testStars = {0, 99, 100, 199, 200, 399, 400, 799, 800, 1599, 1600, 2000};
        StringBuilder sb = new StringBuilder();
        for (int stars : testStars) {
            String naziv = League.forStars(stars).displayName;
            sb.append(stars).append(" ⭐ → ").append(naziv).append("\n");
        }

        // Sve se prikazuje direktno u dijalogu na telefonu, ništa se ne traži u Logcatu.
        new AlertDialog.Builder(context)
                .setTitle("TEST c) Pragovi liga")
                .setMessage(sb.toString().trim())
                .setPositiveButton("OK", null)
                .show();
    }

    // ── d) Napredak u ligu ───────────────────────────────────────────────
    private static void testLeagueUp(Context context) {
        // Pretpostavka: u Firestoru ručno postavljeno stars=95, league=0
        new LeagueRepository().applyStarChange(+15,
                new LeagueRepository.LeagueCallback() {
                    @Override
                    public void onSuccess(LeagueManager.LeagueChangeResult r) {
                        Toast.makeText(context,
                                "TEST d) Stars: " + r.newStars +
                                        " → " + r.newLeague.displayName +
                                        (r.leagueChanged() ? "\n✅ Liga promijenjena!" : "\n– Nema promjene"),
                                Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onError(String msg) {
                        Toast.makeText(context, "Greška: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── e) Mjesečna kazna 30% ────────────────────────────────────────────
    private static void testMonthlyPenalty(Context context) {
        // Pretpostavka: u Firestoru ručno postavljeno stars=430, league=3
        new LeagueRepository().applyMonthlyPenalty(
                new LeagueRepository.LeagueCallback() {
                    @Override
                    public void onSuccess(LeagueManager.LeagueChangeResult r) {
                        Toast.makeText(context,
                                "TEST e) Kazna 30%:\nStars: " + r.newStars +
                                        " → " + r.newLeague.displayName +
                                        (r.leagueChanged() ? "\n✅ Pad u ligi!" : "\n– Nema promjene"),
                                Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onError(String msg) {
                        Toast.makeText(context, "Greška: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}