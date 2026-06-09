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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.sabona.auth.AuthViewModel;
import com.google.android.material.textfield.TextInputEditText;

public class ProfileSettingsTabFragment extends Fragment {

    private TextInputEditText etOldPassword, etNewPassword, etConfirmPassword;
    private Button btnSavePassword;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_tab_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // requireActivity() → isti ViewModel kao u auth fragmentima
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        etOldPassword     = view.findViewById(R.id.etOldPassword);
        etNewPassword     = view.findViewById(R.id.etNewPassword);
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        btnSavePassword   = view.findViewById(R.id.btnSavePassword);
        Button btnLogout  = view.findViewById(R.id.btnLogout);

        // ── Observeri ────────────────────────────────────────────────

        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading ->
                btnSavePassword.setEnabled(!isLoading));

        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty())
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
        });

        viewModel.getPasswordChanged().observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(requireContext(),
                        "Lozinka uspešno promenjena!", Toast.LENGTH_SHORT).show();
                // Očisti polja
                etOldPassword.setText("");
                etNewPassword.setText("");
                etConfirmPassword.setText("");
                viewModel.resetPasswordChanged();
            }
        });

        // ── Click listeneri ──────────────────────────────────────────

        btnSavePassword.setOnClickListener(v -> handlePasswordChange());

        btnLogout.setOnClickListener(v -> {
            // Očisti kešovane podatke profila
            new ViewModelProvider(requireActivity())
                    .get(com.example.sabona.viewModel.ProfileViewModel.class)
                    .clearData();
            viewModel.logout();
            // Navigiraj kroz Activity NavHostFragment (ViewPager2 child ne može sam)
            androidx.navigation.NavController navController =
                    androidx.navigation.Navigation.findNavController(
                            requireActivity(), R.id.navHostFragment);
            navController.navigate(R.id.action_profile_to_login);
        });
    }

    private void handlePasswordChange() {
        String oldPass     = getText(etOldPassword);
        String newPass     = getText(etNewPassword);
        String confirmPass = getText(etConfirmPassword);

        if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(requireContext(), "Popuni sva polja!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.equals(confirmPass)) {
            Toast.makeText(requireContext(), "Lozinke se ne podudaraju!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPass.length() < 6) {
            Toast.makeText(requireContext(), "Lozinka mora imati najmanje 6 znakova!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (oldPass.equals(newPass)) {
            Toast.makeText(requireContext(), "Nova lozinka mora biti različita od stare!", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.changePassword(oldPass, newPass);
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}