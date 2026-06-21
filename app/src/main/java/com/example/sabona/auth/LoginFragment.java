package com.example.sabona.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.R;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

public class LoginFragment extends Fragment {

    private TextInputEditText etLoginIdentifier, etLoginPassword;
    private Button btnLogin;
    private TextView tvGoToRegister, tvPlayAsGuest;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // NAPOMENA: Namjerno NEMA auto-redirect na home čak i ako je korisnik
        // već ulogovan. Ta provjera je ranije izazivala bug – kad bi se
        // LoginFragment ponovo kreirao odmah nakon logout-a (npr. iz
        // ViewPager2 djeteta u Profilu), Firebase Auth state ponekad još
        // nije stigao da se ažurira na svim slušaocima, pa bi
        // getCurrentUser() kratko vratio "starog" korisnika i odmah
        // preusmjerio nazad na Home – kao da logout uopšte nije uspio.
        //
        // Provjera "već ulogovan → idi na home" sada se radi JEDNOM,
        // samo pri startu aplikacije, u MainActivity.onCreate().

        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        etLoginIdentifier = view.findViewById(R.id.etLoginIdentifier);
        etLoginPassword   = view.findViewById(R.id.etLoginPassword);
        btnLogin          = view.findViewById(R.id.btnLogin);
        tvGoToRegister    = view.findViewById(R.id.tvGoToRegister);
        tvPlayAsGuest     = view.findViewById(R.id.tvPlayAsGuest);


        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            btnLogin.setEnabled(!isLoading);
            btnLogin.setText(isLoading ? "Prijava u toku..." : "Prijavi se");
        });

        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getLoginSuccess().observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                viewModel.resetLoginSuccess();
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_login_to_home);
            }
        });


        btnLogin.setOnClickListener(v -> {
            String identifier = etLoginIdentifier.getText() != null
                    ? etLoginIdentifier.getText().toString().trim() : "";
            String password = etLoginPassword.getText() != null
                    ? etLoginPassword.getText().toString().trim() : "";

            if (identifier.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(),
                        "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.login(identifier, password);
        });

        tvGoToRegister.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_login_to_register));

        tvPlayAsGuest.setOnClickListener(v -> {
            tvPlayAsGuest.setEnabled(false);
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(result -> {
                        String uid = result.getUser().getUid();
                        Map<String, Object> data = new HashMap<>();
                        data.put("uid", uid);
                        data.put("username", "Gost" + uid.substring(0, Math.min(5, uid.length())));
                        data.put("isGuest", true);
                        data.put("tokens", 5);
                        data.put("stars", 0);
                        data.put("league", 0);
                        data.put("lastTokenGrantDay", 0L);
                        data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(uid).set(data)
                                .addOnSuccessListener(x -> NavHostFragment.findNavController(this)
                                        .navigate(R.id.action_login_to_home))
                                .addOnFailureListener(e -> {
                                    tvPlayAsGuest.setEnabled(true);
                                    Toast.makeText(requireContext(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        tvPlayAsGuest.setEnabled(true);
                        Toast.makeText(requireContext(), "Greška pri anonimnoj prijavi", Toast.LENGTH_SHORT).show();
                    });
        });
    }
}