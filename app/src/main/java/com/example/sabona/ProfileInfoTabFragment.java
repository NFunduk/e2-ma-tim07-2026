package com.example.sabona;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.sabona.viewModel.ProfileViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Map;

public class ProfileInfoTabFragment extends Fragment {

    private ProfileViewModel viewModel;
    private ImageView imgQrCode;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_tab_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);
        imgQrCode = view.findViewById(R.id.imgQrCode);

        Button btnChangeAvatar = view.findViewById(R.id.btnChangeAvatar);
        btnChangeAvatar.setOnClickListener(v -> openAvatarPicker());

        // Generiši QR čim se učitaju podaci korisnika
        viewModel.getUserData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            String uid = getString(data, "uid", null);
            if (uid == null) {
                // Fallback: iz Firebase Auth
                var user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) uid = user.getUid();
            }
            if (uid != null) generateQr(uid);
        });

        // Ako je korisnik već učitan (tab otvoren poslije), odmah generiši
        Map<String, Object> existing = viewModel.getUserData().getValue();
        if (existing != null) {
            String uid = getString(existing, "uid", null);
            if (uid == null) {
                var user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) uid = user.getUid();
            }
            if (uid != null) generateQr(uid);
        }
    }

    // ─── Avatar picker ─────────────────────────────────────────────────────────
    private void openAvatarPicker() {
        AvatarPickerDialog dialog = AvatarPickerDialog.newInstance();
        dialog.setOnAvatarSelectedListener((resName, resId) -> {
            viewModel.updateAvatar(resName);
            Toast.makeText(requireContext(), "Avatar promijenjen!", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getChildFragmentManager(), "avatar_picker");
    }

    // ─── QR generisanje ────────────────────────────────────────────────────────
    private void generateQr(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 400, 400);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
            // Ukloni tint koji maskira QR (može biti postavljen u XML)
            imgQrCode.setColorFilter(null);
            imgQrCode.setImageTintList(null);
            imgQrCode.setImageBitmap(bmp);
            imgQrCode.setBackgroundColor(Color.WHITE);
        } catch (WriterException e) {
            // ostaviti placeholder
        }
    }

    private String getString(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v instanceof String) ? (String) v : def;
    }
}