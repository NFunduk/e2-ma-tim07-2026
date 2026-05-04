package com.example.sabona;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class NotificationsActivity extends AppCompatActivity {

    private TextView filterAll;
    private TextView filterUnread;
    private TextView filterRead;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.notificationsRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        filterAll = findViewById(R.id.filterAll);
        filterUnread = findViewById(R.id.filterUnread);
        filterRead = findViewById(R.id.filterRead);

        filterAll.setOnClickListener(v -> {
            selectFilter(filterAll, filterUnread, filterRead);
            Toast.makeText(this, "Showing all notifications", Toast.LENGTH_SHORT).show();
        });

        filterUnread.setOnClickListener(v -> {
            selectFilter(filterUnread, filterAll, filterRead);
            Toast.makeText(this, "Showing unread notifications", Toast.LENGTH_SHORT).show();
        });

        filterRead.setOnClickListener(v -> {
            selectFilter(filterRead, filterAll, filterUnread);
            Toast.makeText(this, "Showing read notifications", Toast.LENGTH_SHORT).show();
        });
    }

    private void selectFilter(TextView selected, TextView firstOther, TextView secondOther) {
        selected.setBackgroundResource(R.drawable.filter_selected_bg);
        selected.setTextColor(getColor(R.color.white));

        firstOther.setBackgroundResource(R.drawable.filter_unselected_bg);
        firstOther.setTextColor(getColor(R.color.dark_blue));

        secondOther.setBackgroundResource(R.drawable.filter_unselected_bg);
        secondOther.setTextColor(getColor(R.color.dark_blue));
    }
}