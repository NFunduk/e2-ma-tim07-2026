package com.example.sabona.region;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.sabona.R;
import com.example.sabona.challenge.Challenge;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ekran "Prikaz regiona" (specifikacija, zadatak 5):
 *  a) PRAVA OpenStreetMap mapa sa nasumičnom GPS tačkom za svakog
 *     registrovanog igrača unutar njegovog regiona
 *  b) mesečna rang lista regiona po ukupnom broju osvojenih zvezda u
 *     tekućem ciklusu, sa posebno označenim "tvojim" regionom
 *  c) svaki region ima svoju ikonicu (vidi {@link SerbianRegion})
 *  d) klik na region (poligon na mapi, ili stavka u listi ispod) prikazuje
 *     statistiku regiona: aktivni/ukupno igrača, zvezde u tekućem ciklusu,
 *     i kumulativni broj 1./2./3. mesta kroz sve odigrane cikluse
 *
 * Tačka e) (zlatni/srebrni/bronzani okvir avatara za igrače čiji je
 * region bio 1./2./3. na PRETHODNOM ciklusu) je implementirana u
 * {@code ProfileFragment} (gde se avatar zapravo prikazuje), ne ovde.
 *
 * Koristi osmdroid (OpenStreetMap) — besplatna biblioteka, ne zahteva
 * API key, samo internet konekciju za preuzimanje tile-ova mape.
 */
public class RegionsMapFragment extends Fragment {

    /** Približan centar Srbije, za inicijalno centriranje mape. */
    private static final GeoPoint SERBIA_CENTER = new GeoPoint(44.15, 20.55);
    private static final double INITIAL_ZOOM = 7.3;

    private MapView mapView;
    private ProgressBar progressMap;
    private LinearLayout layoutRegionList;
    private LinearLayout layoutMonthlyRanking;
    private RegionViewModel viewModel;

    private SerbianRegion myRegion;
    /** Region za koji je korisnik kliknuo, a statistika za njega još nije stigla. */
    private SerbianRegion pendingRegionClick;

    private final List<Marker> markers = new ArrayList<>();

    // Tab sadržaj
    private View tabContentMap;
    private View tabContentChallenges;

    // Challenge komponente (tab 2)
    private com.example.sabona.challenge.ChallengeViewModel challengeViewModel;
    private com.example.sabona.challenge.ChallengeListAdapter challengeAdapter;
    private android.widget.ProgressBar progressChallenge;
    private TextView tvRegionLabel;
    private String myUsername;
    private int myStars;
    private int myTokens;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        // osmdroid zahteva inicijalizaciju konfiguracije PRE nego što se
        // ikakav MapView naduva/inflate-uje (čita/piše keš tile-ova na disk).
        Context ctx = requireContext().getApplicationContext();
        Configuration.getInstance().load(ctx,
                android.preference.PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(ctx.getPackageName());

        return inflater.inflate(R.layout.fragment_regions_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView          = view.findViewById(R.id.osmMapView);
        progressMap      = view.findViewById(R.id.progressMap);
        layoutRegionList = view.findViewById(R.id.layoutRegionList);
        layoutMonthlyRanking = view.findViewById(R.id.layoutMonthlyRanking);

        setupMap();

        viewModel = new ViewModelProvider(this).get(RegionViewModel.class);

        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        buildRegionLegend();

        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading ->
                progressMap.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));

        viewModel.getMapPoints().observe(getViewLifecycleOwner(), points ->
                drawPlayerMarkers(points, currentUid));

        viewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty())
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });

        // Jedan trajni observer za statistiku regiona — kad stigne, ako postoji
        // "region na čekanju" (korisnik je kliknuo pre nego što su podaci stigli),
        // otvori dijalog za njega.
        viewModel.getStatsByRegion().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null && pendingRegionClick != null && stats.containsKey(pendingRegionClick)) {
                RegionStatsDialog.newInstance(stats.get(pendingRegionClick))
                        .show(getChildFragmentManager(), "region_stats");
                pendingRegionClick = null;
            }
        });

        // Mesečna rang lista regiona po zvezdama (5.b)
        viewModel.getMonthlyRanking().observe(getViewLifecycleOwner(), this::buildMonthlyRanking);

        viewModel.loadMapPoints();
        viewModel.loadMonthlyRanking();
        loadMyRegion(currentUid);

        // --- Tab switching ---
        tabContentMap        = view.findViewById(R.id.tabContentMap);
        tabContentChallenges = view.findViewById(R.id.tabContentChallenges);
        progressChallenge    = view.findViewById(R.id.progressChallenge);
        tvRegionLabel        = view.findViewById(R.id.tvRegionLabel);

        com.google.android.material.tabs.TabLayout tabLayout =
                view.findViewById(R.id.tabLayoutRegions);

        tabLayout.addOnTabSelectedListener(
                new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                        if (tab.getPosition() == 0) {
                            tabContentMap.setVisibility(View.VISIBLE);
                            tabContentChallenges.setVisibility(View.GONE);
                        } else {
                            tabContentMap.setVisibility(View.GONE);
                            tabContentChallenges.setVisibility(View.VISIBLE);
                            initChallengesTabIfNeeded(currentUid);
                        }
                    }
                    @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
                    @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
                });
    }

    /** Izgradi prikaz mesečne rang liste regiona (5.b), sa istaknutim "tvojim" regionom. */
    private void buildMonthlyRanking(List<RegionMonthlyRankEntry> ranking) {
        if (layoutMonthlyRanking == null || ranking == null) return;
        layoutMonthlyRanking.removeAllViews();

        for (int i = 0; i < ranking.size(); i++) {
            RegionMonthlyRankEntry entry = ranking.get(i);
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_region_monthly_rank, layoutMonthlyRanking, false);

            TextView tvPosition   = row.findViewById(R.id.tvRankPosition);
            TextView tvIcon       = row.findViewById(R.id.tvRankIcon);
            TextView tvRegionName = row.findViewById(R.id.tvRankRegionName);
            TextView tvStars      = row.findViewById(R.id.tvRankStars);

            int position = i + 1;
            tvPosition.setText(position + ".");
            tvIcon.setText(entry.region.icon);
            tvStars.setText(entry.totalStars + " ⭐");

            boolean isMine = entry.region == myRegion;
            String label = isMine
                    ? entry.region.displayName + "  (tvoj region)"
                    : entry.region.displayName;
            tvRegionName.setText(label);
            tvRegionName.setTypeface(null, isMine
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

            if (isMine) {
                row.setBackgroundColor(0x30426BC2); // blago plavo isticanje reda
            }

            layoutMonthlyRanking.addView(row);

            if (i < ranking.size() - 1) {
                View separator = new View(requireContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                params.setMargins(dp(12), 0, dp(12), 0);
                separator.setLayoutParams(params);
                separator.setBackgroundColor(0xFFEEEEEE);
                layoutMonthlyRanking.addView(separator);
            }
        }
    }

    /** Inicijalizuj OpenStreetMap podlogu i nacrtaj granice 6 regiona kao poligone. */
    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(INITIAL_ZOOM);
        mapView.getController().setCenter(SERBIA_CENTER);

        // Mapa je unutar ScrollView-a (cela stranica skroluje) — bez ovoga,
        // prevlačenje prsta unutar mape bi se "svađalo" sa skrolovanjem
        // stranice. Kad korisnik dodirne mapu, privremeno blokiramo
        // roditeljski scroll dok ne podigne prst.
        mapView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false; // pusti dalje da osmdroid sam obradi gest (zoom/pan)
        });

        for (SerbianRegion region : SerbianRegion.values()) {
            Polygon polygon = new Polygon();
            List<GeoPoint> outline = new ArrayList<>();
            for (double[] point : region.boundary) {
                outline.add(new GeoPoint(point[0], point[1]));
            }
            polygon.setPoints(outline);

            polygon.getFillPaint().setColor(withAlpha(region.color, 110));
            polygon.getOutlinePaint().setColor(region.color);
            polygon.getOutlinePaint().setStrokeWidth(3f);

            polygon.setOnClickListener((p, mv, eventPos) -> {
                showRegionStats(region);
                return true;
            });

            mapView.getOverlays().add(polygon);
        }
        mapView.invalidate();
    }

    /** Nacrtaj jedan marker po igraču na njegovoj GPS tački; istakni marker ulogovanog igrača. */
    private void drawPlayerMarkers(List<RegionMapPoint> points, String currentUid) {
        for (Marker marker : markers) mapView.getOverlays().remove(marker);
        markers.clear();

        if (points == null) return;

        for (RegionMapPoint point : points) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(point.lat, point.lon));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setTitle(point.username);

            boolean isMe = currentUid != null && currentUid.equals(point.uid);
            marker.setIcon(buildDotDrawable(isMe));

            mapView.getOverlays().add(marker);
            markers.add(marker);
        }
        mapView.invalidate();
    }

    /** Mala obojena tačka (krug) korišćena kao marker ikona — zlatna za "mene", tamnoplava za ostale. */
    private android.graphics.drawable.Drawable buildDotDrawable(boolean isMe) {
        int sizeDp = isMe ? 16 : 10;
        float density = requireContext().getResources().getDisplayMetrics().density;
        int sizePx = Math.round(sizeDp * density);

        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        fill.setColor(isMe ? 0xFFFFD700 : 0xCC0B1957);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 1.5f, fill);

        android.graphics.Paint stroke = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(android.graphics.Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setColor(Color.WHITE);
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 1.5f, stroke);

        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    /** Učitaj region ulogovanog igrača, samo da bi mogli posebno da ga istaknemo (npr. budući feature e). */
    private void loadMyRegion(String uid) {
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return; // Fragment je u međuvremenu uklonjen sa ekrana
                    myRegion = SerbianRegion.fromDisplayName(doc.getString("region"));
                    // Ponovo izgradi legendu i mesečni rang da se oznaka "tvoj
                    // region" prikaže i ako je ovaj odgovor stigao posle prvog
                    // crtanja tih lista.
                    buildRegionLegend();
                    List<RegionMonthlyRankEntry> cachedRanking = viewModel.getMonthlyRanking().getValue();
                    if (cachedRanking != null) buildMonthlyRanking(cachedRanking);
                });
    }

    /** Klik na region (poligon na mapi, ili stavka u listi ispod) → otvori dijalog sa statistikom. */
    private void showRegionStats(SerbianRegion region) {
        Map<SerbianRegion, RegionStats> cached = viewModel.getStatsByRegion().getValue();
        if (cached != null && cached.containsKey(region)) {
            RegionStatsDialog.newInstance(cached.get(region))
                    .show(getChildFragmentManager(), "region_stats");
            return;
        }

        // Statistika još nije učitana (prvi klik) — zapamti koji je region
        // tražen i učitaj; trajni observer postavljen u onViewCreated će
        // otvoriti dijalog kad podaci stignu.
        pendingRegionClick = region;
        viewModel.loadRegionStats();
    }

    /** Izgradi listu svih regiona ispod mape, klikabilnu kao alternativa direktnom klikku na mapu. */
    private void buildRegionLegend() {
        layoutRegionList.removeAllViews();
        for (SerbianRegion region : SerbianRegion.values()) {
            View row = buildRegionRow(region);
            layoutRegionList.addView(row);

            View separator = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(dp(48), dp(4), 0, dp(4));
            separator.setLayoutParams(params);
            separator.setBackgroundColor(0xFFEEEEEE);
            layoutRegionList.addView(separator);
        }
    }

    private View buildRegionRow(SerbianRegion region) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> showRegionStats(region));

        // Obojeni krug kao indikator boje regiona (poklapa se sa bojom zone na mapi)
        View colorDot = new View(requireContext());
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        dotParams.setMarginEnd(dp(12));
        colorDot.setLayoutParams(dotParams);
        android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
        dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dotBg.setColor(region.color);
        colorDot.setBackground(dotBg);
        row.addView(colorDot);

        // Ikonica
        TextView tvIcon = new TextView(requireContext());
        tvIcon.setText(region.icon);
        tvIcon.setTextSize(18);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.setMarginEnd(dp(10));
        tvIcon.setLayoutParams(iconParams);
        row.addView(tvIcon);

        // Naziv (+ oznaka "moj region")
        TextView tvName = new TextView(requireContext());
        tvName.setTextSize(14);
        tvName.setTextColor(0xFF0B1957);
        boolean isMine = region == myRegion;
        tvName.setText(isMine ? region.displayName + "  (tvoj region)" : region.displayName);
        tvName.setTypeface(null, isMine
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameParams);
        row.addView(tvName);

        // Strelica
        TextView tvArrow = new TextView(requireContext());
        tvArrow.setText("›");
        tvArrow.setTextSize(18);
        tvArrow.setTextColor(0xFFBDBDBD);
        row.addView(tvArrow);

        return row;
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private int dp(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private boolean challengesInitialized = false;

    private void initChallengesTabIfNeeded(String uid) {
        if (challengesInitialized || uid == null) return;
        challengesInitialized = true;

        // Učitaj korisničke podatke za kreiranje/pridruživanje izazovu
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || doc == null) return;
                    myUsername = doc.getString("username");
                    myStars    = doc.getLong("stars")  != null ? doc.getLong("stars").intValue()  : 0;
                    myTokens   = doc.getLong("tokens") != null ? doc.getLong("tokens").intValue() : 0;

                    if (myRegion != null) {
                        tvRegionLabel.setText("Izazovi za region: " + myRegion);
                        challengeViewModel.startListening(myRegion.displayName);
                    }
                });

        // RecyclerView
        androidx.recyclerview.widget.RecyclerView rv =
                requireView().findViewById(R.id.rvChallenges);
        challengeAdapter = new com.example.sabona.challenge.ChallengeListAdapter(
                uid, challenge -> showJoinChallengeDialog(challenge));
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        rv.setAdapter(challengeAdapter);

        // ViewModel
        challengeViewModel = new ViewModelProvider(this)
                .get(com.example.sabona.challenge.ChallengeViewModel.class);

        challengeViewModel.getChallenges().observe(getViewLifecycleOwner(),
                list -> challengeAdapter.setItems(list));
        challengeViewModel.getError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });
        challengeViewModel.getLoading().observe(getViewLifecycleOwner(), isLoading ->
                progressChallenge.setVisibility(
                        Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));

        // FAB — novi izazov
        requireView().findViewById(R.id.fabNewChallenge)
                .setOnClickListener(v -> showCreateChallengeDialog());
    }

    private void showCreateChallengeDialog() {
        if (myRegion == null) {
            Toast.makeText(requireContext(), "Sačekaj da se učitaju podaci...", Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, 0);

        TextView tvStarsLabel = new TextView(requireContext());
        tvStarsLabel.setText("Ulog zvezda: 1");
        tvStarsLabel.setTextColor(0xFF0B1957);
        layout.addView(tvStarsLabel);

        android.widget.SeekBar sbStars = new android.widget.SeekBar(requireContext());
        sbStars.setMax(9);
        sbStars.setProgress(0);
        sbStars.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean f) {
                tvStarsLabel.setText("Ulog zvezda: " + (p + 1));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        layout.addView(sbStars);

        TextView tvTokensLabel = new TextView(requireContext());
        tvTokensLabel.setText("Ulog tokena: 1");
        tvTokensLabel.setTextColor(0xFF0B1957);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        tvTokensLabel.setLayoutParams(lp);
        layout.addView(tvTokensLabel);

        android.widget.SeekBar sbTokens = new android.widget.SeekBar(requireContext());
        sbTokens.setMax(1);
        sbTokens.setProgress(0);
        sbTokens.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean f) {
                tvTokensLabel.setText("Ulog tokena: " + (p + 1));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        layout.addView(sbTokens);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Novi izazov")
                .setView(layout)
                .setPositiveButton("Pošalji", (d, w) -> {
                    int stars  = sbStars.getProgress()  + 1;
                    int tokens = sbTokens.getProgress() + 1;
                    if (myStars < stars) {
                        Toast.makeText(requireContext(), "Nemaš dovoljno zvezda!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (myTokens < tokens) {
                        Toast.makeText(requireContext(), "Nemaš dovoljno tokena!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String creatorUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
                    com.example.sabona.challenge.Challenge ch =
                            new com.example.sabona.challenge.Challenge(
                                    creatorUid, myUsername, myRegion.displayName, stars, tokens);
                    challengeViewModel.createChallenge(ch);
                    challengeViewModel.getChallenges().observe(getViewLifecycleOwner(), list -> {
                        if (list != null && !list.isEmpty()) {
                            Challenge latest = list.get(0); // najnoviji (sortirano po createdAt desc)
                            if (latest.getCreatorUid().equals(creatorUid) && latest.getId() != null) {
                                startChallengeGame(latest.getId());
                            }
                        }
                    });
                    myStars  -= stars;
                    myTokens -= tokens;
                })
                .setNegativeButton("Odustani", null)
                .show();
    }

    private void showJoinChallengeDialog(com.example.sabona.challenge.Challenge challenge) {
        if (myStars < challenge.getStarsWager()) {
            Toast.makeText(requireContext(), "Nemaš dovoljno zvezda!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (myTokens < challenge.getTokensWager()) {
            Toast.makeText(requireContext(), "Nemaš dovoljno tokena!", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Prihvati izazov?")
                .setMessage("Ulog: " + challenge.getStarsWager() + " ⭐ i "
                        + challenge.getTokensWager() + " 🪙\n\n"
                        + "Kreator: " + challenge.getCreatorUsername() + "\n"
                        + "Učesnici: " + challenge.getParticipantCount() + "/4")
                .setPositiveButton("Prihvati", (d, w) -> {
                    challengeViewModel.joinChallenge(challenge.getId(), currentUid, myUsername,
                            challenge.getStarsWager(), challenge.getTokensWager());
                    myStars  -= challenge.getStarsWager();
                    myTokens -= challenge.getTokensWager();

                    // Pokreni solo partiju sa challengeId-em
                    startChallengeGame(challenge.getId());
                })
                .setNegativeButton("Odustani", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) mapView.onDetach();
        if (challengeViewModel != null) challengeViewModel.stopListening();
    }

    private void startChallengeGame(String challengeId) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        // Kreiramo solo sesiju — igrač je i player1 i player2
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(com.example.sabona.game.GameSessionManager.COL_GAME_SESSIONS)
                .add(new java.util.HashMap<String, Object>() {{
                    put("player1Uid", uid);
                    put("player2Uid", uid); // solo — isti igrač
                    put("isFriendlyMatch", true); // ne ulazi u statistiku
                    put("challengeId", challengeId);
                    put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                }})
                .addOnSuccessListener(ref -> {
                    if (!isAdded()) return;
                    com.example.sabona.game.GameSessionManager.get().setupAsSolo(ref.getId());

                    Bundle args = new Bundle();
                    args.putString("sessionId", ref.getId());
                    args.putBoolean("isHost", true);
                    args.putString("challengeId", challengeId);
                    androidx.navigation.Navigation
                            .findNavController(requireView())
                            .navigate(R.id.action_regions_to_kozna_challenge, args);
                });
    }
}