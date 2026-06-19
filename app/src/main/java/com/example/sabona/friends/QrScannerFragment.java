package com.example.sabona.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.navigation.Navigation;

import com.example.sabona.R;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

/**
 * Fragment za QR skeniranje prijatelja.
 * Vraća skenirani UID putem Fragment Result API (ključ "qr_result", bundle key "scanned_uid").
 *
 * Koristi zxing-android-embedded. Dodaj u build.gradle:
 *   implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
 */
public class QrScannerFragment extends Fragment {

    private DecoratedBarcodeView barcodeView;
    private boolean resultDelivered = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_qr_scanner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        barcodeView = view.findViewById(R.id.barcodeScanner);

        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (resultDelivered) return; // Ne isporuči dva puta
                resultDelivered = true;

                String scannedText = result.getText();
                if (scannedText == null || scannedText.trim().isEmpty()) {
                    Toast.makeText(requireContext(), "Nije moguće pročitati QR kod.", Toast.LENGTH_SHORT).show();
                    resultDelivered = false;
                    return;
                }

                // Isporuči rezultat roditeljskom fragmentu
                Bundle bundle = new Bundle();
                bundle.putString("scanned_uid", scannedText.trim());
                getParentFragmentManager().setFragmentResult("qr_result", bundle);

                // Vrati se nazad kroz NavController (fragment je dio nav grafa)
                requireActivity().runOnUiThread(() ->
                        Navigation.findNavController(requireView()).popBackStack());
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        resultDelivered = false;
        if (barcodeView != null) barcodeView.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (barcodeView != null) barcodeView.pause();
    }
}