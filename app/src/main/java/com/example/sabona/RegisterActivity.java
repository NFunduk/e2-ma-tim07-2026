package com.example.sabona;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {
    private ImageView btnBack;
    private TextInputEditText etEmail, etUsername, etPassword, etPasswordConfirm;
    private Spinner spinnerRegion;
    private android.widget.Button btnRegister;
    private TextView tvGoToLogin;

    private final String[] regions = {
        "Vojvodina" , "Beograd", "Šumadija", "Zapadna Srbija", "Južna i Istočna Srbija", "Kosovo i Metohija"
    };

    @Override
    protected void onCreate(Bundle savedInstaceState){
        super.onCreate(savedInstaceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupRegionSpinner();
        setupListeners();
    }

    private void initViews(){
        btnBack = findViewById(R.id.btnBack);
        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm);
        spinnerRegion = findViewById(R.id.spinnerRegion);
        btnRegister=findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
    }

    private void setupRegionSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, regions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRegion.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            String passwordConfirm = etPasswordConfirm.getText() != null ? etPasswordConfirm.getText().toString().trim() : "";
            String region = (String) spinnerRegion.getSelectedItem();

            if (email.isEmpty() || username.isEmpty() || password.isEmpty() || passwordConfirm.isEmpty()) {
                Toast.makeText(this, "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(passwordConfirm)) {
                Toast.makeText(this, "Lozinke se ne poklapaju", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this, "Lozinka mora imati najmanje 6 znakova", Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: Pozvati Firebase registraciju
            // Nakon uspešne registracije, idi na ekran za potvrdu mejla
            Toast.makeText(this, "Registracija uspešna! Proveri email.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(RegisterActivity.this, VerifyEmailActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
            finish();
        });
    }
}
