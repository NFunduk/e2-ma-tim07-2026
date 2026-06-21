package com.example.sabona.friends;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sabona.R;
import com.example.sabona.model.FriendUser;

import java.util.ArrayList;

public class FriendsFragment extends Fragment {

    private FriendsViewModel viewModel;

    private RecyclerView rvFriends;
    private RecyclerView rvSearch;
    private EditText     etSearch;
    private TextView     tvSearchHint;
    private TextView     tvFriendsTitle;

    private FriendsAdapter friendsAdapter;
    private FriendsAdapter searchAdapter;

    // Aktivni game invite (da igrač može da otkaže)
    private String pendingGameRequestId = null;

    // Launcher za QR skeniranje
    private ActivityResultLauncher<String> cameraPermLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openQrScanner();
                    } else {
                        Toast.makeText(requireContext(),
                                "Dozvola za kameru je potrebna za QR skeniranje",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(FriendsViewModel.class);

        etSearch      = view.findViewById(R.id.etSearchFriend);
        tvSearchHint  = view.findViewById(R.id.tvSearchHint);
        tvFriendsTitle = view.findViewById(R.id.tvFriendsTitle);
        rvFriends     = view.findViewById(R.id.rvFriends);
        rvSearch      = view.findViewById(R.id.rvSearchResults);
        ImageButton btnQr = view.findViewById(R.id.btnQrScan);

        // Adapteri
        friendsAdapter = new FriendsAdapter(new ArrayList<>(), this::onFriendAction, false);
        searchAdapter  = new FriendsAdapter(new ArrayList<>(), this::onSearchAction, true);

        getParentFragmentManager().setFragmentResultListener(
                "qr_result",
                getViewLifecycleOwner(),
                (requestKey, bundle) -> {

                    String scannedUid = bundle.getString("scanned_uid");

                    if (scannedUid != null && !scannedUid.isEmpty()) {

                        viewModel.lookupByUid(scannedUid);

                        rvFriends.setVisibility(View.GONE);
                        rvSearch.setVisibility(View.VISIBLE);
                        tvFriendsTitle.setVisibility(View.GONE);

                        Toast.makeText(
                                requireContext(),
                                "QR uspešno skeniran",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        rvFriends.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvFriends.setAdapter(friendsAdapter);

        rvSearch.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSearch.setAdapter(searchAdapter);

        // Pretraga
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    viewModel.clearSearch();
                    showFriendsList(true);
                } else {
                    viewModel.search(q);
                    showFriendsList(false);
                }
            }
        });

        // QR dugme
        btnQr.setOnClickListener(v -> requestCameraAndScan());

        // Observeri
        viewModel.getFriends().observe(getViewLifecycleOwner(), friends -> {
            friendsAdapter.setItems(friends != null ? friends : new ArrayList<>());
            tvFriendsTitle.setText("Prijatelji (" + (friends != null ? friends.size() : 0) + ")");
        });

        viewModel.getSearchResults().observe(getViewLifecycleOwner(), results -> {
            if (results == null) {
                searchAdapter.setItems(new ArrayList<>());
                tvSearchHint.setVisibility(View.GONE);
                showFriendsList(true);
            } else {
                searchAdapter.setItems(results);
                showFriendsList(false);
                tvSearchHint.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.getPendingGameRequestId().observe(getViewLifecycleOwner(), reqId -> {
            pendingGameRequestId = reqId;
            if (reqId != null) showCancelInviteDialog(reqId);
        });

        viewModel.getGameInviteStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null) return;
            switch (status) {
                case "accepted":
                    Toast.makeText(requireContext(),
                            "Prijatelj je prihvatio poziv! Partija počinje...",
                            Toast.LENGTH_LONG).show();
                    // navigacija se dešava u matchSessionReady observeru ispod
                    break;
                case "rejected":
                    Toast.makeText(requireContext(),
                            "Prijatelj je odbio poziv.",
                            Toast.LENGTH_SHORT).show();
                    viewModel.clearGameInviteStatus();
                    break;
                case "cancelled":
                    viewModel.clearGameInviteStatus();
                    break;
            }
        });

        viewModel.getMatchSessionReady().observe(getViewLifecycleOwner(), sessionId -> {
            if (sessionId == null) return;

            Bundle args = new Bundle();
            args.putString("sessionId", sessionId);
            args.putBoolean("isHost", true); // pošiljalac poziva je uvek host prijateljske partije

            viewModel.clearGameInviteStatus();
            viewModel.clearMatchSessionReady();

            Navigation.findNavController(requireView())
                    .navigate(R.id.action_friends_to_koZnaZna, args);
        });

        // Učitaj prijatelje
        viewModel.loadFriends();
    }

    // ─── akcije ─────────────────────────────────────────────────────────────

    /** Pozove se za stavke u listi prijatelja */
    private void onFriendAction(FriendUser friend, int action) {
        if (action == FriendsAdapter.ACTION_INVITE) {
            if (pendingGameRequestId != null) {
                Toast.makeText(requireContext(),
                        "Već imaš aktivan poziv. Otkaži ga prvo.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Poziv za partiju")
                    .setMessage("Pozvati " + friend.getUsername() + " na partiju?")
                    .setPositiveButton("Pozovi", (d, w) -> viewModel.sendGameInvite(friend))
                    .setNegativeButton("Odustani", null)
                    .show();
        }
    }

    /** Pozove se za stavke u rezultatima pretrage */
    private void onSearchAction(FriendUser user, int action) {
        if (action == FriendsAdapter.ACTION_ADD) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Dodaj prijatelja")
                    .setMessage("Poslati zahtev za prijateljstvo korisniku " + user.getUsername() + "?")
                    .setPositiveButton("Pošalji", (d, w) -> viewModel.sendFriendRequest(user))
                    .setNegativeButton("Odustani", null)
                    .show();
        }
    }

    // ─── QR skeniranje ──────────────────────────────────────────────────────

    private void requestCameraAndScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openQrScanner();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openQrScanner() {
        Navigation.findNavController(requireView())
                .navigate(R.id.action_friends_to_qrScanner);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void showFriendsList(boolean showFriends) {
        if (showFriends) {
            tvFriendsTitle.setVisibility(View.VISIBLE);
            rvFriends.setVisibility(View.VISIBLE);
            rvSearch.setVisibility(View.GONE);
        } else {
            tvFriendsTitle.setVisibility(View.GONE);
            rvFriends.setVisibility(View.GONE);
            rvSearch.setVisibility(View.VISIBLE);
        }
    }

    private void showCancelInviteDialog(String reqId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Poziv poslan")
                .setMessage("Čekamo odgovor prijatelja. Otkazati poziv?")
                .setPositiveButton("Otkaži poziv", (d, w) -> viewModel.cancelGameInvite(reqId))
                .setNegativeButton("Čekaj", null)
                .setCancelable(false)
                .show();
    }
}