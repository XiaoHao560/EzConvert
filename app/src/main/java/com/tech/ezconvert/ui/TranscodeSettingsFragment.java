package com.tech.ezconvert.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.ConfigManager;

public class TranscodeSettingsFragment extends BaseFragment {
    
    private MaterialSwitch hardwareAccelerationSwitch;
    private MaterialSwitch multithreadingSwitch;
    private Button saveSettingsBtn;
    private ConfigManager configManager;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transcode_settings, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        configManager = ConfigManager.getInstance(requireContext());
        initializeViews(view);
        loadSettings();
        
        saveSettingsBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            saveSettings();
        });
    }
    
    private void initializeViews(View view) {
        hardwareAccelerationSwitch = view.findViewById(R.id.hardware_acceleration_switch);
        multithreadingSwitch = view.findViewById(R.id.multithreading_switch);
        saveSettingsBtn = view.findViewById(R.id.save_settings_btn);
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
        
        Toast.makeText(requireContext(), "设置已保存到配置文件", Toast.LENGTH_SHORT).show();
        navigateUp();
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