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

public class ProfileInfoTabFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_tab_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnChangeAvatar = view.findViewById(R.id.btnChangeAvatar);
        btnChangeAvatar.setOnClickListener(v -> {
            // TODO: Otvoriti picker za avatar
            Toast.makeText(requireContext(), "Odabir avatara – uskoro!", Toast.LENGTH_SHORT).show();
        });
    }
}