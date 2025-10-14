package com.tech.ezconvert;

import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

public class TranscodeSettingsActivity extends AppCompatActivity {
    
    private static final String PREFS_NAME = "EzConvertSettings";
    private static final String KEY_HARDWARE_ACCELERATION = "hardware_acceleration";
    private static final String KEY_MULTITHREADING = "multithreading";
    
    private Switch hardwareAccelerationSwitch;
    private Switch multithreadingSwitch;
    private Button saveSettingsBtn;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        initializeViews();
        loadSettings();
        
        saveSettingsBtn.setOnClickListener(v -> saveSettings());
    }
    
    private void initializeViews() {
        hardwareAccelerationSwitch = findViewById(R.id.hardware_acceleration_switch);
        multithreadingSwitch = findViewById(R.id.multithreading_switch);
        saveSettingsBtn = findViewById(R.id.save_settings_btn);
    }
    
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean hardwareAcceleration = prefs.getBoolean(KEY_HARDWARE_ACCELERATION, false);
        boolean multithreading = prefs.getBoolean(KEY_MULTITHREADING, true);
        
        hardwareAccelerationSwitch.setChecked(hardwareAcceleration);
        multithreadingSwitch.setChecked(multithreading);
    }
    
    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putBoolean(KEY_HARDWARE_ACCELERATION, hardwareAccelerationSwitch.isChecked());
        editor.putBoolean(KEY_MULTITHREADING, multithreadingSwitch.isChecked());
        editor.apply();
        
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    // 静态方法供其他类读取设置
    public static boolean isHardwareAccelerationEnabled(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_HARDWARE_ACCELERATION, false);
    }
    
    public static boolean isMultithreadingEnabled(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_MULTITHREADING, true);
    }
}