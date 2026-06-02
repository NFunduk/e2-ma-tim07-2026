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
import com.google.android.material.textfield.TextInputEditText;

public class ProfileSettingsTabFragment extends Fragment {

    private TextInputEditText etOldPassword;
    private TextInputEditText etNewPassword;
    private TextInputEditText etConfirmPassword;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_tab_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etOldPassword     = view.findViewById(R.id.etOldPassword);
        etNewPassword     = view.findViewById(R.id.etNewPassword);
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        Button btnSavePassword = view.findViewById(R.id.btnSavePassword);
        Button btnLogout       = view.findViewById(R.id.btnLogout);

        btnSavePassword.setOnClickListener(v -> handlePasswordChange());

        btnLogout.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Odjavljeni ste!", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_profile_to_login);
        });
    }

    private void handlePasswordChange() {
        String oldPass     = etOldPassword.getText()     != null ? etOldPassword.getText().toString().trim()     : "";
        String newPass     = etNewPassword.getText()     != null ? etNewPassword.getText().toString().trim()     : "";
        String confirmPass = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(requireContext(), "Popuni sva polja!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.equals(confirmPass)) {
            Toast.makeText(requireContext(), "Lozinke se ne podudaraju!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPass.length() < 6) {
            Toast.makeText(requireContext(), "Lozinka je prekratka!", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Pozvati logiku za promjenu lozinke
        Toast.makeText(requireContext(), "Lozinka promijenjena!", Toast.LENGTH_SHORT).show();

        etOldPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");
    }
}