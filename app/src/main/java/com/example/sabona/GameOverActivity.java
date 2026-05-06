package com.example.sabona;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class GameOverActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_over);

        String winner = getIntent().getStringExtra("winner");
        if (winner == null) winner = "Kraj igre!";

        TextView tvWinner = findViewById(R.id.tvGameOverWinner);
        Button btnHome = findViewById(R.id.btnGameOverHome);
        Button btnPlayAgain = findViewById(R.id.btnPlayAgain);

        tvWinner.setText(winner);

        btnHome.setOnClickListener(v -> {
            startActivity(new Intent(GameOverActivity.this, MainActivity.class));
            finish();
        });

        btnPlayAgain.setOnClickListener(v -> {
            startActivity(new Intent(GameOverActivity.this, KoZnaZnaActivity.class));
            finish();
        });
    }
}
