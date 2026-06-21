package com.example.sabona.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.R;

public class VerifyEmailFragment extends Fragment {

    private Button btnCheckVerification, btnResendEmail;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verify_email, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        btnCheckVerification = view.findViewById(R.id.btnCheckVerification);
        btnResendEmail       = view.findViewById(R.id.btnResendEmail);


        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading ->
                btnCheckVerification.setEnabled(!isLoading));

        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getVerifiedSuccess().observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                viewModel.resetVerifiedSuccess();
                Toast.makeText(requireContext(),
                        "Email potvrđen! Možeš se prijaviti.",
                        Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_verify_to_login);
            }
        });

        viewModel.getEmailResent().observe(getViewLifecycleOwner(), sent -> {
            if (Boolean.TRUE.equals(sent)) {
                Toast.makeText(requireContext(),
                        "Email je ponovo poslat! Proveri inbox.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnCheckVerification.setOnClickListener(v -> viewModel.checkVerification());

        btnResendEmail.setOnClickListener(v -> viewModel.resendEmail());
    }
}