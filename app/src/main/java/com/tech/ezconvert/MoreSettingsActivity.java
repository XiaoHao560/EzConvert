package com.tech.ezconvert;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class MoreSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more_settings);

        LinearLayout logEntry = findViewById(R.id.item_run_log);
        logEntry.setOnClickListener(v -> {
            startActivity(new Intent(this, LogSettingsActivity.class));
        });
    }
}
