package com.tech.ezconvert.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.ConfigManager;

public class TranscodeSettingsActivity extends AppCompatActivity {
    
    private MaterialSwitch hardwareAccelerationSwitch;
    private MaterialSwitch multithreadingSwitch;
    private Button saveSettingsBtn;
    private ConfigManager configManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        
        configManager = ConfigManager.getInstance(this);
        initializeViews();
        loadSettings();
        
        saveSettingsBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            saveSettings();
        });
    }
    
    private void initializeViews() {
        hardwareAccelerationSwitch = findViewById(R.id.hardware_acceleration_switch);
        multithreadingSwitch = findViewById(R.id.multithreading_switch);
        saveSettingsBtn = findViewById(R.id.save_settings_btn);
    }
    
    private void loadSettings() {
        boolean hardwareAcceleration = configManager.isHardwareAccelerationEnabled();
        boolean multithreading = configManager.isMultithreadingEnabled();
        
        hardwareAccelerationSwitch.setChecked(hardwareAcceleration);
        multithreadingSwitch.setChecked(multithreading);
    }
    
    private void saveSettings() {
        configManager.setHardwareAccelerationEnabled(hardwareAccelerationSwitch.isChecked());
        configManager.setMultithreadingEnabled(multithreadingSwitch.isChecked());
        
        Toast.makeText(this, "设置已保存到配置文件", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
    
    public static boolean isHardwareAccelerationEnabled(android.content.Context context) {
        ConfigManager config = ConfigManager.getInstance(context);
        return config.isHardwareAccelerationEnabled();
    }
    
    public static boolean isMultithreadingEnabled(android.content.Context context) {
        ConfigManager config = ConfigManager.getInstance(context);
        return config.isMultithreadingEnabled();
    }
}