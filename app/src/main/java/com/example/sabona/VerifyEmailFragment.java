package com.example.sabona;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class VerifyEmailFragment extends Fragment {

    private Button btnCheckVerification, btnResendEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verify_email, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnCheckVerification = view.findViewById(R.id.btnCheckVerification);
        btnResendEmail       = view.findViewById(R.id.btnResendEmail);

        btnCheckVerification.setOnClickListener(v -> {
            // TODO: Firebase provera
            Toast.makeText(requireContext(), "Proveravamo potvrdu...", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_verify_to_login);
        });

        btnResendEmail.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Email je ponovo poslat!", Toast.LENGTH_SHORT).show());
    }
}