package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.sabona.viewModel.ProfileViewModel;
import com.example.sabona.widget.DonutProgressView;
import com.example.sabona.widget.MiniBarChartView;

import java.util.Map;

/**
 * Tab "Statistika" – učitava podatke iz Firestore (kolekcija "stats").
 *
 * Očekivana struktura dokumenta stats/{uid}:
 * {
 *   totalGames: 87,
 *   wins: 54, losses: 33,
 *
 *   // Ko zna zna
 *   kzzMinPts: 10, kzzMaxPts: 40,
 *   kzzCorrect: 120, kzzWrong: 30,
 *
 *   // Spojnice
 *   spojMinPts: 4, spojMaxPts: 16,
 *   spojConnected: 78, spojTotal: 100,
 *
 *   // Asocijacije
 *   asocMinPts: 5, asocMaxPts: 50,
 *   asocSolved: 14, asocUnsolved: 5,
 *
 *   // Skočko (pogođeno u pokuš. 1-2, 3-4, 5-6)
 *   skockoMinPts: 0, skockoMaxPts: 30,
 *   skockoAttempts12: 5, skockoAttempts34: 8, skockoAttempts56: 4, skockoTotal: 20,
 *
 *   // Korak po korak
 *   korakMinPts: 2, korakMaxPts: 18,
 *   korakStep: [10, 8, 6, 4, 2, 1, 0],  // koliko puta pogođeno u svakom koraku
 *   korakTotal: 40,
 *
 *   // Moj broj
 *   mojBrojMinPts: 0, mojBrojMaxPts: 10,
 *   mojBrojCorrect: 22, mojBrojTotal: 40
 * }
 */
public class ProfileStatsTabFragment extends Fragment {

    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_tab_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);

        // Ako već imamo podatke (tab switch)
        Map<String, Object> existing = viewModel.getStatsData().getValue();
        if (existing != null) {
            bindStats(view, existing);
        }

        // Observe za svježe podatke
        viewModel.getStatsData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) bindStats(view, data);
        });

        // Ako nema ni user podataka ni stats, prikaži nule (ne blokira UI)
        viewModel.getUserData().observe(getViewLifecycleOwner(), userData -> {
            if (viewModel.getStatsData().getValue() == null) {
                bindStats(view, new java.util.HashMap<>());
            }
        });
    }

    private void bindStats(View view, Map<String, Object> d) {
        // ── Opšta statistika ──────────────────────────────────────────────
        long total  = getLong(d, "totalGames", 0);
        long wins   = getLong(d, "wins", 0);
        long losses = getLong(d, "losses", 0);

        tv(view, R.id.tvTotalGames).setText(String.valueOf(total));

        if (total > 0) {
            int winPct  = (int) (wins   * 100 / total);
            int lossPct = (int) (losses * 100 / total);
            tv(view, R.id.tvWinPercent).setText(winPct + "%");
            tv(view, R.id.tvLossPercent).setText(lossPct + "%");
        } else {
            tv(view, R.id.tvWinPercent).setText("0%");
            tv(view, R.id.tvLossPercent).setText("0%");
        }

        // ── Ko zna zna ────────────────────────────────────────────────────
        long kzzMin = getLong(d, "kzzMinPts", 0);
        long kzzMax = getLong(d, "kzzMaxPts", 0);
        tv(view, R.id.tvStatsKoZnaZna).setText(kzzMin + " - " + kzzMax + " bod.");

        long kzzC = getLong(d, "kzzCorrect", 0);
        long kzzW = getLong(d, "kzzWrong", 0);
        tv(view, R.id.tvStatsKzzRatio).setText(kzzC + " / " + kzzW);

        long kzzTotal = kzzC + kzzW;
        int kzzPct = kzzTotal > 0 ? (int) (kzzC * 100 / kzzTotal) : 0;
        donut(view, R.id.donutKoZnaZna).setPercent(kzzPct);

        // ── Spojnice ──────────────────────────────────────────────────────
        long spojMin = getLong(d, "spojMinPts", 0);
        long spojMax = getLong(d, "spojMaxPts", 0);
        tv(view, R.id.tvStatsSpojnice).setText(spojMin + " - " + spojMax + " bod.");

        long spojConn  = getLong(d, "spojConnected", 0);
        long spojTotal = getLong(d, "spojTotal", 0);
        int spojPctInt = spojTotal > 0 ? (int) (spojConn * 100 / spojTotal) : 0;
        tv(view, R.id.tvStatsSpojnicePercent).setText(spojPctInt + "%");
        donut(view, R.id.donutSpojnice).setPercent(spojPctInt);

        // ── Asocijacije ───────────────────────────────────────────────────
        long asocMin = getLong(d, "asocMinPts", 0);
        long asocMax = getLong(d, "asocMaxPts", 0);
        tv(view, R.id.tvStatsAsocijacije).setText(asocMin + " - " + asocMax + " bod.");

        long asocSol   = getLong(d, "asocSolved", 0);
        long asocUnsol = getLong(d, "asocUnsolved", 0);
        tv(view, R.id.tvStatsAsocijacijeRatio).setText(asocSol + " / " + asocUnsol);

        long asocTotal = asocSol + asocUnsol;
        int asocPct = asocTotal > 0 ? (int) (asocSol * 100 / asocTotal) : 0;
        donut(view, R.id.donutAsocijacije).setPercent(asocPct);

        // ── Skočko ────────────────────────────────────────────────────────
        long skMin = getLong(d, "skockoMinPts", 0);
        long skMax = getLong(d, "skockoMaxPts", 0);
        tv(view, R.id.tvStatsSkocko).setText(skMin + " - " + skMax + " bod.");

        long sk12  = getLong(d, "skockoAttempts12", 0);
        long sk34  = getLong(d, "skockoAttempts34", 0);
        long sk56  = getLong(d, "skockoAttempts56", 0);
        long skTot = getLong(d, "skockoTotal", 1); // izbjegni /0
        if (skTot == 0) skTot = 1;
        int sk12Pct = (int) (sk12 * 100 / skTot);
        int sk34Pct = (int) (sk34 * 100 / skTot);
        int sk56Pct = (int) (sk56 * 100 / skTot);
        String skPct = sk12Pct + "% / " + sk34Pct + "% / " + sk56Pct + "%";
        tv(view, R.id.tvStatsSkockoPercent).setText(skPct);
        barChart(view, R.id.barChartSkocko).setPercents(new int[]{sk12Pct, sk34Pct, sk56Pct});

        // ── Korak po korak ────────────────────────────────────────────────
        long kMin = getLong(d, "korakMinPts", 0);
        long kMax = getLong(d, "korakMaxPts", 0);
        tv(view, R.id.tvStatsKorakPoKorak).setText(kMin + " - " + kMax + " bod.");

        // Čita flat polja korakStep0..korakStep6
        long korakTot = getLong(d, "korakTotal", 1);
        if (korakTot == 0) korakTot = 1;
        boolean hasAnyKorakData = false;
        StringBuilder korakSb = new StringBuilder();
        int[] korakPercents = new int[7];
        for (int i = 0; i < 7; i++) {
            long cnt = getLong(d, "korakStep" + i, 0);
            if (cnt > 0) hasAnyKorakData = true;
            int pct = (int) (cnt * 100 / korakTot);
            korakPercents[i] = pct;
            korakSb.append(pct).append("% k.").append(i + 1);
            if (i < 6) korakSb.append(" / ");
        }
        tv(view, R.id.tvStatsKorakPercent).setText(hasAnyKorakData ? korakSb.toString() : "—");
        barChart(view, R.id.barChartKorak).setPercents(korakPercents);

        // ── Moj broj ──────────────────────────────────────────────────────
        long mbMin = getLong(d, "mojBrojMinPts", 0);
        long mbMax = getLong(d, "mojBrojMaxPts", 0);
        tv(view, R.id.tvStatsMojBroj).setText(mbMin + " - " + mbMax + " bod.");

        long mbCorr  = getLong(d, "mojBrojCorrect", 0);
        long mbTotal = getLong(d, "mojBrojTotal", 1);
        if (mbTotal == 0) mbTotal = 1;
        int mbPct = (int) (mbCorr * 100 / mbTotal);
        tv(view, R.id.tvStatsMojBrojPercent).setText(mbPct + "%");
        donut(view, R.id.donutMojBroj).setPercent(mbPct);
    }

    // ─── Utils ─────────────────────────────────────────────────────────────────
    private DonutProgressView donut(View root, int id) {
        return root.findViewById(id);
    }

    private MiniBarChartView barChart(View root, int id) {
        return root.findViewById(id);
    }
    private TextView tv(View root, int id) {
        return root.findViewById(id);
    }

    private long getLong(Map<String, Object> map, String key, long def) {
        Object v = map.get(key);
        if (v instanceof Long)    return (Long) v;
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Double)  return ((Double) v).longValue();
        return def;
    }
}