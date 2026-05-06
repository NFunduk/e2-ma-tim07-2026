package com.example.sabona;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private ImageView btnNotifications, btnProfile;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnNotifications = findViewById(R.id.btnNotifications);
        btnProfile = findViewById(R.id.btnProfile);
        bottomNav = findViewById(R.id.bottomNav);

        btnNotifications.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, NotificationsActivity.class));
        });

        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        });

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.home) {
                return true;
            } else if (id == R.id.play) {
                startActivity(new Intent(MainActivity.this, AssociationsActivity.class));
                return true;
            } else if (id == R.id.profile) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                return true;
            } else if (id == R.id.rank) {
                return true;
            } else if (id == R.id.friends) {
                return true;
            }

            return false;
        });
    }
}