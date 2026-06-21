package com.example.sabona.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.R;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterFragment extends Fragment {

    private TextInputEditText etEmail, etUsername, etPassword, etPasswordConfirm;
    private Spinner spinnerRegion;
    private Button btnRegister;
    private TextView tvGoToLogin;
    private AuthViewModel viewModel;

    private final String[] regions = {
            "Vojvodina", "Beograd", "Šumadija", "Zapadna Srbija",
            "Južna i Istočna Srbija", "Kosovo i Metohija"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        etEmail           = view.findViewById(R.id.etEmail);
        etUsername        = view.findViewById(R.id.etUsername);
        etPassword        = view.findViewById(R.id.etPassword);
        etPasswordConfirm = view.findViewById(R.id.etPasswordConfirm);
        spinnerRegion     = view.findViewById(R.id.spinnerRegion);
        btnRegister       = view.findViewById(R.id.btnRegister);
        tvGoToLogin       = view.findViewById(R.id.tvGoToLogin);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                regions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRegion.setAdapter(adapter);


        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            btnRegister.setEnabled(!isLoading);
            btnRegister.setText(isLoading ? "Registracija u toku..." : "Registruj se");
        });

        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getRegisterSuccess().observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                viewModel.resetRegisterSuccess();
                Toast.makeText(requireContext(),
                        "Registracija uspešna! Proveri email.",
                        Toast.LENGTH_LONG).show();
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_register_to_verify);
            }
        });


        tvGoToLogin.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_register_to_login));

        btnRegister.setOnClickListener(v -> {
            String email    = getText(etEmail);
            String username = getText(etUsername);
            String password = getText(etPassword);
            String confirm  = getText(etPasswordConfirm);
            String region   = spinnerRegion.getSelectedItem().toString();

            if (email.isEmpty() || username.isEmpty()
                    || password.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(requireContext(),
                        "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }
            if (username.length() < 3) {
                Toast.makeText(requireContext(),
                        "Korisničko ime mora imati najmanje 3 znaka",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(requireContext(),
                        "Lozinke se ne poklapaju", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(requireContext(),
                        "Lozinka mora imati najmanje 6 znakova",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.register(email, username, password, region);
        });
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}