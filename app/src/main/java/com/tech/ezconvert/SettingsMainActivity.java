package com.tech.ezconvert;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

public class SettingsMainActivity extends AppCompatActivity {
    
    private LinearLayout transcodeSettingsItem;
    private LinearLayout generalSettingsItem;
    private LinearLayout aboutItem;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_main);
        
        initializeViews();
        setupClickListeners();
    }
    
    private void initializeViews() {
        transcodeSettingsItem = findViewById(R.id.transcode_settings_item);
        generalSettingsItem = findViewById(R.id.general_settings_item);
        aboutItem = findViewById(R.id.about_item);
    }
    
    private void setupClickListeners() {
        transcodeSettingsItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开转码设置界面
                Intent intent = new Intent(SettingsMainActivity.this, TranscodeSettingsActivity.class);
                startActivity(intent);
            }
        });
        
        // 为其他设置项添加点击事件（暂时显示提示）
        View.OnClickListener comingSoonListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(SettingsMainActivity.this, "该功能将在后续版本中提供", Toast.LENGTH_SHORT).show();
            }
        };
        
        generalSettingsItem.setOnClickListener(comingSoonListener);
        aboutItem.setOnClickListener(comingSoonListener);
    }
}