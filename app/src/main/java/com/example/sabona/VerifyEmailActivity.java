package com.example.sabona;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class VerifyEmailActivity extends AppCompatActivity {
    private Button btnCheckVerification, btnResendEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        btnCheckVerification = findViewById(R.id.btnCheckVerification);
        btnResendEmail = findViewById(R.id.btnResendEmail);

        btnCheckVerification.setOnClickListener(v -> {
            // TODO: Proveriti da li je email potvrđen u Firebaseu
            Toast.makeText(this, "Proveravamo potvrdu...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(VerifyEmailActivity.this, LoginActivity.class));
            finish();
        });

        btnResendEmail.setOnClickListener(v -> {
            // TODO: Poslati verifikacioni mejl ponovo
            Toast.makeText(this, "Email je ponovo poslat!", Toast.LENGTH_SHORT).show();
        });
    }
}
