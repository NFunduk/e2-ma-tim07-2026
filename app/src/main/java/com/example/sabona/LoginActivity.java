package com.example.sabona;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etLoginIdentifier, etLoginPassword;
    private android.widget.Button btnLogin;
    private TextView tvForgotPassword, tvGoToRegister, tvPlayAsGuest;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etLoginIdentifier = findViewById(R.id.etLoginIdentifier);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        tvPlayAsGuest = findViewById(R.id.tvPlayAsGuest);
    }

    private void setupListeners(){
        btnLogin.setOnClickListener(v -> {
            String identifier = etLoginIdentifier.getText() != null ? etLoginIdentifier.getText().toString().trim() : "";
            String password = etLoginPassword.getText() != null ? etLoginPassword.getText().toString().trim() : "";

            if(identifier.isEmpty() || password.isEmpty()){
                Toast.makeText(this, "Popuni sva polja", Toast.LENGTH_SHORT).show();
                return;
            }

            //TODO: Pozvati Firebase logovanje
            Toast.makeText(this, "Prijava uspešna!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        tvForgotPassword.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ResetPasswordActivity.class));
        });

        tvGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        tvPlayAsGuest.setOnClickListener(v -> {
            Toast.makeText(this, "Nastavljaš kao gost", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });
    }
}
