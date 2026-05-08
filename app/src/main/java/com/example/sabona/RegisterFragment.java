package com.example.sabona;

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
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputEditText;

public class RegisterFragment extends Fragment {

    private TextInputEditText etEmail, etUsername, etPassword, etPasswordConfirm;
    private Spinner spinnerRegion;
    private Button btnRegister;
    private TextView tvGoToLogin;

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

        etEmail           = view.findViewById(R.id.etEmail);
        etUsername        = view.findViewById(R.id.etUsername);
        etPassword        = view.findViewById(R.id.etPassword);
        etPasswordConfirm = view.findViewById(R.id.etPasswordConfirm);
        spinnerRegion     = view.findViewById(R.id.spinnerRegion);
        btnRegister       = view.findViewById(R.id.btnRegister);
        tvGoToLogin       = view.findViewById(R.id.tvGoToLogin);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, regions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRegion.setAdapter(adapter);

        tvGoToLogin.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_register_to_login));

        btnRegister.setOnClickListener(v -> {
            String email           = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String username        = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
            String password        = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            String passwordConfirm = etPasswordConfirm.getText() != null ? etPasswordConfirm.getText().toString().trim() : "";

            if (email.isEmpty() || username.isEmpty() || password.isEmpty() || passwordConfirm.isEmpty()) {
                Toast.makeText(requireContext(), "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(passwordConfirm)) {
                Toast.makeText(requireContext(), "Lozinke se ne poklapaju", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(requireContext(), "Lozinka mora imati najmanje 6 znakova", Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: Firebase
            Toast.makeText(requireContext(), "Registracija uspešna! Proveri email.", Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_register_to_verify);
        });
    }
}