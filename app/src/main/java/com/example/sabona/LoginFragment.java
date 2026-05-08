package com.example.sabona;

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
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputEditText;

public class LoginFragment extends Fragment {

    private TextInputEditText etLoginIdentifier, etLoginPassword;
    private Button btnLogin;
    private TextView tvGoToRegister, tvPlayAsGuest;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etLoginIdentifier = view.findViewById(R.id.etLoginIdentifier);
        etLoginPassword   = view.findViewById(R.id.etLoginPassword);
        btnLogin          = view.findViewById(R.id.btnLogin);
        tvGoToRegister    = view.findViewById(R.id.tvGoToRegister);
        tvPlayAsGuest     = view.findViewById(R.id.tvPlayAsGuest);

        btnLogin.setOnClickListener(v -> {
            String identifier = etLoginIdentifier.getText() != null ?
                    etLoginIdentifier.getText().toString().trim() : "";
            String password = etLoginPassword.getText() != null ?
                    etLoginPassword.getText().toString().trim() : "";

            if (identifier.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: Firebase
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_login_to_home);
        });

        tvGoToRegister.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_login_to_register));

        tvPlayAsGuest.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_login_to_home));
    }
}